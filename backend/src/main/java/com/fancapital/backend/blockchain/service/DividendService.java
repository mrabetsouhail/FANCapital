package com.fancapital.backend.blockchain.service;

import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service de distribution des coupons/dividendes. Mod AST 2.2 : Auto-remboursement par Coupons.
 * Tout coupon versé sur des titres séquestrés est prioritairement affecté au remboursement de l'avance.
 */
@Service
public class DividendService {

  private static final Logger log = LoggerFactory.getLogger(DividendService.class);
  private static final BigInteger TND_SCALE = BigInteger.valueOf(100_000_000L);

  private final DebtManager debtManager;

  public DividendService(DebtManager debtManager) {
    this.debtManager = debtManager;
  }

  /**
   * Distribue un coupon à un utilisateur. Si l'utilisateur a une avance AST active,
   * le coupon est prioritairement affecté au remboursement (DebtManager).
   *
   * @param userWallet Adresse wallet du bénéficiaire
   * @param amountTnd  Montant du coupon en TND (scale 1e8)
   * @return montant affecté au remboursement (0 si pas d'avance active)
   */
  public BigInteger distributeCoupon(String userWallet, BigInteger amountTnd) {
    if (amountTnd == null || amountTnd.signum() <= 0) return BigInteger.ZERO;

    BigInteger appliedToLoan = debtManager.applyRepaymentFromCoupon(userWallet, amountTnd);

    if (appliedToLoan.signum() > 0) {
      log.info("Coupon {} TND affecté au remboursement AST pour {}", appliedToLoan, userWallet);
      return appliedToLoan;
    }

    // Pas d'avance active : le coupon serait versé au Credit Wallet (implémentation future)
    return BigInteger.ZERO;
  }

  /**
   * Version avec montant en TND (double).
   */
  public double distributeCouponTnd(String userWallet, double amountTnd) {
    BigInteger scaled = BigInteger.valueOf((long) (amountTnd * 1e8));
    return debtManager.applyRepaymentFromCoupon(userWallet, scaled).doubleValue() / 1e8;
  }
}
