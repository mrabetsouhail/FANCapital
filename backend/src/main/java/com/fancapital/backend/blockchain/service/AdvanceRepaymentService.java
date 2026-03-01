package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.config.SpecFinancieresV47;
import com.fancapital.backend.blockchain.model.AdvanceInterestTracking;
import com.fancapital.backend.blockchain.model.InvestorProfileDtos.InvestorProfileResponse;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import com.fancapital.backend.blockchain.repo.AdvanceInterestTrackingRepository;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service pour le remboursement partiel d'une avance sur titres depuis le Cash Wallet.
 * Intérêts fixes dès le début, prélevés en priorité depuis le premier remboursement.
 * Flux Matrice : capital → Piscine A (pool), intérêts → Piscine C (Compte de Revenus).
 */
@Service
public class AdvanceRepaymentService {

  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);
  private static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365);

  private final BlockchainReadService readService;
  private final DebtManager debtManager;
  private final BurnKeyService burnKey;
  private final MintKeyService mintKey;
  private final CreditWriteService creditWrite;
  private final DeploymentRegistry registry;
  private final CompartmentsService compartmentsService;
  private final AdvanceInterestTrackingRepository interestTrackingRepo;

  public AdvanceRepaymentService(
      BlockchainReadService readService,
      DebtManager debtManager,
      BurnKeyService burnKey,
      MintKeyService mintKey,
      CreditWriteService creditWrite,
      DeploymentRegistry registry,
      CompartmentsService compartmentsService,
      AdvanceInterestTrackingRepository interestTrackingRepo) {
    this.readService = readService;
    this.debtManager = debtManager;
    this.burnKey = burnKey;
    this.mintKey = mintKey;
    this.creditWrite = creditWrite;
    this.registry = registry;
    this.compartmentsService = compartmentsService;
    this.interestTrackingRepo = interestTrackingRepo;
  }

  /**
   * Rembourse une partie de l'avance depuis le Cash Wallet.
   * Capital → Piscine A (pool), Intérêts → Piscine C.
   *
   * @param userWallet   Adresse wallet de l'utilisateur
   * @param amountTnd1e8 Montant en TND (scaled 1e8)
   * @return Hash de la transaction recordRepayment
   */
  public String repayFromCashWallet(String userWallet, BigInteger amountTnd1e8) throws IOException {
    if (userWallet == null || userWallet.isBlank() || amountTnd1e8 == null || amountTnd1e8.signum() <= 0) {
      throw new IllegalArgumentException("Paramètres invalides pour le remboursement.");
    }

    var adv = debtManager.getActiveAdvanceForUser(userWallet);
    if (adv == null) {
      throw new IllegalStateException("Aucune avance active pour ce wallet.");
    }
    if ("B".equals(adv.model())) {
      throw new IllegalStateException("Le modèle PGP (B) ne supporte pas les remboursements partiels. L'avance sera clôturée à l'échéance par l'opérateur.");
    }
    var loan = adv.loan();
    int feeLevel = getFeeLevel(userWallet);
    Optional<AdvanceInterestTracking> trackingOpt = interestTrackingRepo.findByLoanId(loan.loanId().toString());

    BigInteger maxRepay;
    BigInteger principalPart;
    BigInteger interestPart;

    if (trackingOpt.isPresent()) {
      AdvanceInterestTracking t = trackingOpt.get();
      BigInteger totalInterest = new BigInteger(t.getTotalInterestTnd1e8());
      BigInteger interestPaid = new BigInteger(t.getInterestPaidTnd1e8());
      BigInteger remainingInterest = totalInterest.subtract(interestPaid).max(BigInteger.ZERO);
      maxRepay = loan.principalTnd().add(remainingInterest);
      if (amountTnd1e8.compareTo(maxRepay) > 0) {
        throw new IllegalArgumentException("Le montant dépasse le total à rembourser (principal + intérêts). Max: "
            + (maxRepay.doubleValue() / PRICE_SCALE.doubleValue()) + " TND.");
      }
      interestPart = amountTnd1e8.min(remainingInterest);
      principalPart = amountTnd1e8.subtract(interestPart).min(loan.principalTnd());
      interestPart = amountTnd1e8.subtract(principalPart);
    } else {
      long daysSinceStart = Math.max(0, (System.currentTimeMillis() / 1000 - loan.startAt()) / 86400);
      maxRepay = loan.principalTnd().add(computeAccruedInterest(loan.principalTnd(), feeLevel, daysSinceStart));
      if (amountTnd1e8.compareTo(maxRepay) > 0) {
        throw new IllegalArgumentException("Le montant dépasse le total à rembourser (principal + intérêts). Max: "
            + (maxRepay.doubleValue() / PRICE_SCALE.doubleValue()) + " TND.");
      }
      interestPart = computeAccruedInterest(amountTnd1e8, feeLevel, daysSinceStart).min(amountTnd1e8);
      principalPart = amountTnd1e8.subtract(interestPart).min(loan.principalTnd());
      interestPart = amountTnd1e8.subtract(principalPart);
    }

    PortfolioResponse portfolio = readService.portfolio(userWallet);
    BigInteger cashBal = new BigInteger(portfolio.cashBalanceTnd());
    if (cashBal.compareTo(amountTnd1e8) < 0) {
      throw new IllegalStateException("Solde Cash Wallet insuffisant. Disponible: "
          + (cashBal.doubleValue() / PRICE_SCALE.doubleValue()) + " TND.");
    }

    Optional<CompartmentsService.MatriceInfo> matriceOpt = compartmentsService.getMatrice();

    if (matriceOpt.isPresent()) {
      // 1. Burn TND from user
      burnKey.burn(userWallet, amountTnd1e8);

      // 2. Mint capital → Piscine A (pool du token collatéral)
      var fund = registry.findByToken(loan.token()).orElse(null);
      if (fund != null && principalPart.signum() > 0) {
        String poolAddr = fund.pool();
        if (poolAddr != null && !poolAddr.isBlank()) {
          mintKey.mint(poolAddr, principalPart);
        }
      }

      // 3. Mint intérêts → Piscine C (Compte de Revenus)
      String piscineC = matriceOpt.get().piscineC();
      if (piscineC != null && !piscineC.isBlank() && interestPart.signum() > 0) {
        mintKey.mint(piscineC, interestPart);
      }
    } else {
      // Fallback : burn uniquement (comportement legacy)
      principalPart = amountTnd1e8;
      interestPart = BigInteger.ZERO;
      burnKey.burn(userWallet, amountTnd1e8);
    }

    // 4. Mettre à jour les intérêts payés (modèle fixe)
    if (trackingOpt.isPresent() && interestPart.signum() > 0) {
      AdvanceInterestTracking t = trackingOpt.get();
      BigInteger newPaid = new BigInteger(t.getInterestPaidTnd1e8()).add(interestPart);
      t.setInterestPaidTnd1e8(newPaid.toString());
      interestTrackingRepo.save(t);
    }

    // 5. Record repayment on-chain (principal uniquement)
    return creditWrite.recordRepayment(loan.loanId(), principalPart);
  }

  private int getFeeLevel(String userWallet) {
    try {
      InvestorProfileResponse profile = readService.investorProfile(userWallet);
      return profile != null ? Math.min(Math.max(profile.feeLevel(), 0), 4) : 0;
    } catch (Exception e) {
      return 1; // SILVER par défaut
    }
  }

  /** Intérêts proportionnels : amount * rate% * days / 365. Spec v4.7 (SILVER 5%, GOLD 4.5%, etc.). */
  private BigInteger computeAccruedInterest(BigInteger amount, int feeLevel, long daysSinceStart) {
    if (feeLevel <= 0 || feeLevel >= SpecFinancieresV47.TIER_INTEREST_RATES.length) {
      return BigInteger.ZERO;
    }
    double rate = SpecFinancieresV47.TIER_INTEREST_RATES[feeLevel];
    if (rate <= 0 || daysSinceStart <= 0) return BigInteger.ZERO;
    // interest = amount * rate * days / (365 * 100), rate en %
    return amount
        .multiply(BigInteger.valueOf((long) (rate * 100)))
        .multiply(BigInteger.valueOf(daysSinceStart))
        .divide(DAYS_PER_YEAR.multiply(BigInteger.valueOf(10_000)));
  }
}
