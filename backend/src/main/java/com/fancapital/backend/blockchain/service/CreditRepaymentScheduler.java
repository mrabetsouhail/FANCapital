package com.fancapital.backend.blockchain.service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduler for repayment calendar: identifies loans with due payments.
 * Runs daily; logs loans whose next installment or maturity is due.
 * Actual repayment recording is done via CreditWriteService when cash/coupon is received.
 */
@Service
@ConditionalOnProperty(name = "blockchain.credit.scheduler-enabled", havingValue = "true", matchIfMissing = false)
public class CreditRepaymentScheduler {

  private static final Logger log = LoggerFactory.getLogger(CreditRepaymentScheduler.class);
  private static final long SECONDS_PER_DAY = 86_400;

  private final CreditReadService creditRead;

  public CreditRepaymentScheduler(CreditReadService creditRead) {
    this.creditRead = creditRead;
  }

  @Scheduled(cron = "${blockchain.credit.scheduler-cron:0 0 2 * * ?}") // 2 AM daily by default
  public void checkDuePayments() {
    try {
      if (creditRead.getNextLoanId().compareTo(BigInteger.ONE) < 0) return;
    } catch (Exception e) {
      return; // CreditModelA not configured
    }

    List<CreditReadService.LoanInfo> active = creditRead.listActiveLoans();
    long now = Instant.now().getEpochSecond();

    for (CreditReadService.LoanInfo loan : active) {
      long startAt = loan.startAt();
      long durationDays = loan.durationDays();
      long endAt = startAt + durationDays * SECONDS_PER_DAY;

      // Maturity due
      if (now >= endAt) {
        log.info("AST loan {} maturity due: user={}, principal={} (1e8 TND)",
            loan.loanId(), loan.user(), loan.principalTnd());
        continue;
      }

      // Monthly installments (simplified: one installment per 30 days)
      int installments = Math.max(1, (int) (durationDays / 30));
      for (int i = 1; i <= installments; i++) {
        long dueAt = startAt + (i * SECONDS_PER_DAY * 30L);
        if (now >= dueAt && dueAt < endAt) {
          log.info("AST loan {} installment {}/{} due: user={}",
              loan.loanId(), i, installments, loan.user());
        }
      }
    }
  }
}
