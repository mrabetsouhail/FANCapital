package com.fancapital.backend.blockchain.service;

import java.io.IOException;
import java.math.BigInteger;
import org.springframework.stereotype.Service;

/**
 * Active une demande d'avance : crédite le Credit Wallet (mint TND) puis appelle activateAdvance.
 * L'opérateur doit appeler cet endpoint après qu'un utilisateur ait soumis requestAdvance.
 */
@Service
public class CreditAdvanceActivationService {

  private final CreditReadService creditRead;
  private final CreditWriteService creditWrite;
  private final MintKeyService mintKey;

  public CreditAdvanceActivationService(CreditReadService creditRead, CreditWriteService creditWrite,
      MintKeyService mintKey) {
    this.creditRead = creditRead;
    this.creditWrite = creditWrite;
    this.mintKey = mintKey;
  }

  /**
   * Crédite le principal au wallet utilisateur et active l'avance (lock collatéral).
   *
   * @param loanId ID du prêt (status Requested)
   * @return hash de la tx activateAdvance
   */
  public String activateAndCredit(BigInteger loanId) {
    CreditReadService.LoanInfo loan = creditRead.getLoan(loanId);
    if (loan == null) {
      throw new IllegalArgumentException("Prêt introuvable: " + loanId);
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

    // 1. Mint TND au wallet utilisateur (= Credit Wallet, même adresse que Cash Wallet pour MVP)
    mintKey.mint(user, loan.principalTnd());

    // 2. Activer l'avance (lock collatéral)
    try {
      return creditWrite.activateAdvance(loanId);
    } catch (IOException e) {
      throw new IllegalStateException("Échec activateAdvance: " + e.getMessage(), e);
    }
  }
}
