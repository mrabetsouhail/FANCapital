package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import java.io.IOException;
import java.math.BigInteger;
import org.springframework.stereotype.Service;

/**
 * Service pour le remboursement partiel d'une avance sur titres depuis le Cash Wallet.
 * 1. Vérifie le solde Cash Wallet
 * 2. Burn des TND de l'utilisateur
 * 3. Enregistre le remboursement on-chain (recordRepayment)
 */
@Service
public class AdvanceRepaymentService {

  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);

  private final BlockchainReadService readService;
  private final DebtManager debtManager;
  private final BurnKeyService burnKey;
  private final CreditWriteService creditWrite;

  public AdvanceRepaymentService(
      BlockchainReadService readService,
      DebtManager debtManager,
      BurnKeyService burnKey,
      CreditWriteService creditWrite) {
    this.readService = readService;
    this.debtManager = debtManager;
    this.burnKey = burnKey;
    this.creditWrite = creditWrite;
  }

  /**
   * Rembourse une partie de l'avance depuis le Cash Wallet.
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
    if (amountTnd1e8.compareTo(loan.principalTnd()) > 0) {
      throw new IllegalArgumentException("Le montant dépasse le principal restant.");
    }

    PortfolioResponse portfolio = readService.portfolio(userWallet);
    BigInteger cashBal = new BigInteger(portfolio.cashBalanceTnd());
    if (cashBal.compareTo(amountTnd1e8) < 0) {
      throw new IllegalStateException("Solde Cash Wallet insuffisant. Disponible: "
          + (cashBal.doubleValue() / PRICE_SCALE.doubleValue()) + " TND.");
    }

    // 1. Burn TND from user
    burnKey.burn(userWallet, amountTnd1e8);

    // 2. Record repayment on-chain
    return creditWrite.recordRepayment(loan.loanId(), amountTnd1e8);
  }
}
