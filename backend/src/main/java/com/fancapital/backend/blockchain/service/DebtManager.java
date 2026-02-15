package com.fancapital.backend.blockchain.service;

import java.math.BigInteger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates debt repayment with CreditModelA. Coupons on sequestered tokens go to loan first. */
@Service
public class DebtManager {

  private static final Logger log = LoggerFactory.getLogger(DebtManager.class);

  private final CreditReadService creditRead;
  private final CreditWriteService creditWrite;

  public DebtManager(CreditReadService creditRead, CreditWriteService creditWrite) {
    this.creditRead = creditRead;
    this.creditWrite = creditWrite;
  }

  /** Apply coupon to user's active AST loan. Returns amount applied. */
  public BigInteger applyRepaymentFromCoupon(String userWallet, BigInteger amountTnd) {
    if (userWallet == null || userWallet.isBlank() || amountTnd == null || amountTnd.signum() <= 0) {
      return BigInteger.ZERO;
    }
    String n = userWallet.trim().toLowerCase();
    List<CreditReadService.LoanInfo> active = creditRead.listActiveLoans();
    CreditReadService.LoanInfo loan = active.stream()
        .filter(l -> l.user().equalsIgnoreCase(n))
        .findFirst()
        .orElse(null);
    if (loan == null) return BigInteger.ZERO;
    BigInteger toApply = amountTnd.min(loan.principalTnd());
    if (toApply.signum() <= 0) return BigInteger.ZERO;
    try {
      String tx = creditWrite.recordRepayment(loan.loanId(), toApply);
      log.info("Coupon->AST loan {}: {} TND tx={}", loan.loanId(), toApply, tx);
      return toApply;
    } catch (Exception e) {
      log.warn("recordRepayment failed loan {}: {}", loan.loanId(), e.getMessage());
      return BigInteger.ZERO;
    }
  }

  /** Avance active (Model A ou B). */
  public CreditReadService.ActiveAdvanceResult getActiveAdvanceForUser(String userWallet) {
    return creditRead.getActiveAdvanceForUser(userWallet);
  }

  /** @deprecated Préférer getActiveAdvanceForUser pour le modèle. */
  public CreditReadService.LoanInfo getActiveLoanForUser(String userWallet) {
    CreditReadService.ActiveAdvanceResult r = getActiveAdvanceForUser(userWallet);
    return r != null ? r.loan() : null;
  }
}
