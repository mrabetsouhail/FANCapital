package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.config.SpecFinancieresV47;
import com.fancapital.backend.blockchain.model.AdvanceInterestTracking;
import com.fancapital.backend.blockchain.model.InvestorProfileDtos.InvestorProfileResponse;
import com.fancapital.backend.blockchain.repo.AdvanceInterestTrackingRepository;
import java.io.IOException;
import java.math.BigInteger;
import org.springframework.stereotype.Service;

/**
 * Active une demande d'avance : crédite le Credit Wallet (mint TND) puis appelle activateAdvance.
 * Les intérêts sont fixes dès le début (principal × taux × durée / 365) et prélevés en priorité.
 */
@Service
public class CreditAdvanceActivationService {

  private static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365);

  private final CreditReadService creditRead;
  private final CreditWriteService creditWrite;
  private final MintKeyService mintKey;
  private final BlockchainReadService blockchainRead;
  private final AdvanceInterestTrackingRepository interestTrackingRepo;

  public CreditAdvanceActivationService(CreditReadService creditRead, CreditWriteService creditWrite,
      MintKeyService mintKey, BlockchainReadService blockchainRead,
      AdvanceInterestTrackingRepository interestTrackingRepo) {
    this.creditRead = creditRead;
    this.creditWrite = creditWrite;
    this.mintKey = mintKey;
    this.blockchainRead = blockchainRead;
    this.interestTrackingRepo = interestTrackingRepo;
  }

  /**
   * Crédite le principal au wallet utilisateur et active l'avance (lock collatéral).
   *
   * @param loanId ID du prêt (status Requested)
   * @param model  "A" ou "B" (PGP)
   * @return hash de la tx activateAdvance
   */
  public String activateAndCredit(BigInteger loanId, String model) {
    String m = (model != null && !model.isBlank()) ? model.trim().toUpperCase() : "A";
    boolean isB = "B".equals(m);

    CreditReadService.LoanInfo loan = isB ? creditRead.getLoanB(loanId) : creditRead.getLoan(loanId);
    if (loan == null) {
      throw new IllegalArgumentException("Prêt introuvable: " + loanId + " (modèle " + m + ")");
    }
    if (loan.status() != 0) { // Status.Requested = 0
      throw new IllegalStateException("Le prêt n'est pas en attente (status=" + loan.status() + "). Seuls les prêts Requested peuvent être activés.");
    }
    if (loan.principalTnd() == null || loan.principalTnd().signum() <= 0) {
      throw new IllegalStateException("Principal invalide pour le prêt " + loanId);
    }
    String user = loan.user();
    if (user == null || user.isBlank()) {
      throw new IllegalStateException("Utilisateur invalide pour le prêt " + loanId);
    }

    String tokenAddr = loan.token();
    BigInteger collateralAmount = loan.collateralAmount();
    BigInteger available = blockchainRead.getAvailableTokenBalance(tokenAddr, user);
    if (available.compareTo(collateralAmount) < 0) {
      throw new IllegalStateException(
          "L'utilisateur ne possède pas suffisamment de tokens pour le collateral. Disponible: "
              + available + ", requis: " + collateralAmount + ". Annulez la demande ou attendez que l'utilisateur acquière les tokens.");
    }

    // 1. Activer l'avance (lock collatéral) AVANT de créditer
    String txHash;
    try {
      txHash = isB ? creditWrite.activateAdvanceB(loanId) : creditWrite.activateAdvance(loanId);
    } catch (IOException e) {
      throw new IllegalStateException("Échec activateAdvance (vérifiez que l'utilisateur possède les tokens en collateral): " + e.getMessage(), e);
    }

    // 2. Créditer le Credit Wallet une fois le collatéral verrouillé
    mintKey.mint(user, loan.principalTnd());

    // 3. Créer le suivi des intérêts fixes (principal × taux × durée / 365)
    if (!isB) {
      createInterestTracking(loan, user);
    }

    return txHash;
  }

  private void createInterestTracking(CreditReadService.LoanInfo loan, String user) {
    int feeLevel = getFeeLevel(user);
    if (feeLevel <= 0 || feeLevel >= SpecFinancieresV47.TIER_INTEREST_RATES.length) return;
    double rate = SpecFinancieresV47.TIER_INTEREST_RATES[feeLevel];
    if (rate <= 0 || loan.durationDays() <= 0) return;

    BigInteger totalInterest = loan.principalTnd()
        .multiply(BigInteger.valueOf((long) (rate * 100)))
        .multiply(BigInteger.valueOf(loan.durationDays()))
        .divide(DAYS_PER_YEAR.multiply(BigInteger.valueOf(10_000)));

    AdvanceInterestTracking t = new AdvanceInterestTracking();
    t.setLoanId(loan.loanId().toString());
    t.setOriginalPrincipalTnd1e8(loan.principalTnd().toString());
    t.setTotalInterestTnd1e8(totalInterest.toString());
    t.setInterestPaidTnd1e8("0");
    interestTrackingRepo.save(t);
  }

  private int getFeeLevel(String userWallet) {
    try {
      InvestorProfileResponse profile = blockchainRead.investorProfile(userWallet);
      return profile != null ? Math.min(Math.max(profile.feeLevel(), 0), 4) : 1;
    } catch (Exception e) {
      return 1;
    }
  }

  /** @deprecated Préférer {@link #activateAndCredit(java.math.BigInteger, String)} avec model explicite. */
  public String activateAndCredit(BigInteger loanId) {
    return activateAndCredit(loanId, "A");
  }
}
