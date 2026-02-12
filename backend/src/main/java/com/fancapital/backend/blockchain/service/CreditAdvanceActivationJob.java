package com.fancapital.backend.blockchain.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Auto-active les demandes d'avance en attente : crédite le Credit Wallet puis lock collatéral.
 * Désactivé par défaut ; activer avec blockchain.credit.auto-activate=true
 */
@Service
@ConditionalOnProperty(name = "blockchain.credit.auto-activate", havingValue = "true", matchIfMissing = false)
public class CreditAdvanceActivationJob {

  private static final Logger log = LoggerFactory.getLogger(CreditAdvanceActivationJob.class);

  private final CreditReadService creditRead;
  private final CreditAdvanceActivationService activation;

  public CreditAdvanceActivationJob(CreditReadService creditRead, CreditAdvanceActivationService activation) {
    this.creditRead = creditRead;
    this.activation = activation;
  }

  @Scheduled(fixedDelayString = "${blockchain.credit.auto-activate-interval-ms:60000}") // 1 min default
  public void processRequestedAdvances() {
    List<CreditReadService.LoanInfo> requested = creditRead.listRequestedLoans();
    for (CreditReadService.LoanInfo loan : requested) {
      try {
        String txHash = activation.activateAndCredit(loan.loanId());
        log.info("AST auto-activated loan {}: tx={}, user={}", loan.loanId(), txHash, loan.user());
      } catch (Exception e) {
        log.warn("AST auto-activation failed for loan {}: {}", loan.loanId(), e.getMessage());
      }
    }
  }
}
