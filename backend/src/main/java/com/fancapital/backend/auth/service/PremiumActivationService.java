package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.config.SpecFinancieresV47;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import com.fancapital.backend.blockchain.service.BurnKeyService;
import com.fancapital.backend.blockchain.service.InvestorRegistryWriteService;
import com.fancapital.backend.blockchain.service.SciScorePushService;
import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.model.Notification.Type;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active l'abonnement Premium (versement via Cash Wallet).
 * L'utilisateur choisit la durée (trimestriel, semestriel, annuel) selon la grille Spec v4.7.
 * Le montant est prélevé du Cash Wallet (burn) et apparaît dans l'historique des transactions.
 */
@Service
public class PremiumActivationService {

  /** Durée: 0=trimestriel, 1=semestriel, 2=annuel */
  public enum SubscriptionDuration {
    TRIMESTRIEL(0),
    SEMESTRIEL(1),
    ANNUEL(2);

    public final int index;

    SubscriptionDuration(int index) {
      this.index = index;
    }

    public static SubscriptionDuration from(String value) {
      if (value == null || value.isBlank()) return ANNUEL;
      String v = value.trim().toLowerCase();
      if (v.startsWith("trim")) return TRIMESTRIEL;
      if (v.startsWith("sem")) return SEMESTRIEL;
      if (v.startsWith("ann")) return ANNUEL;
      return ANNUEL;
    }
  }

  private final AppUserRepository userRepo;
  private final BlockchainReadService blockchainRead;
  private final BurnKeyService burnKey;
  private final InvestorRegistryWriteService registryWrite;
  private final SciScorePushService sciPush;
  private final NotificationService notificationService;

  public PremiumActivationService(
      AppUserRepository userRepo,
      BlockchainReadService blockchainRead,
      BurnKeyService burnKey,
      InvestorRegistryWriteService registryWrite,
      SciScorePushService sciPush,
      NotificationService notificationService
  ) {
    this.userRepo = userRepo;
    this.blockchainRead = blockchainRead;
    this.burnKey = burnKey;
    this.registryWrite = registryWrite;
    this.sciPush = sciPush;
    this.notificationService = notificationService;
  }

  /**
   * Active l'abonnement Premium pour l'utilisateur.
   *
   * @param userId    Identifiant utilisateur
   * @param duration Trimestriel, semestriel ou annuel
   * @return Message de confirmation
   */
  @Transactional
  public String activateForUser(String userId, SubscriptionDuration duration) {
    AppUser user = userRepo.findById(userId).orElse(null);
    if (user == null) {
      throw new IllegalArgumentException("Utilisateur introuvable");
    }
    // Renouvellement autorisé : on prolonge même si déjà actif
    String wallet = user.getWalletAddress();
    if (wallet == null || wallet.isBlank() || !wallet.startsWith("0x") || wallet.length() != 42) {
      throw new IllegalArgumentException("Wallet non configuré. Complétez votre profil et liez un wallet.");
    }

    // Récupérer le feeLevel on-chain pour la grille tarifaire
    var profile = blockchainRead.investorProfile(wallet);
    int feeLevel = profile != null ? profile.feeLevel() : 0;
    if (feeLevel < 0 || feeLevel > 4) {
      feeLevel = 0;
    }

    int amountTnd = getSubscriptionAmountTnd(feeLevel, duration);
    if (amountTnd <= 0) {
      throw new IllegalArgumentException(
          "Aucun abonnement Premium disponible pour votre tier (BRONZE). Passez Silver ou supérieur.");
    }

    BigInteger amount1e8 = BigInteger.valueOf(amountTnd).multiply(BigInteger.valueOf(100_000_000));

    var port = blockchainRead.portfolio(wallet);
    BigInteger cash = new BigInteger(port.cashBalanceTnd() != null ? port.cashBalanceTnd() : "0");
    if (cash.compareTo(amount1e8) < 0) {
      throw new IllegalArgumentException(
          "Solde Cash Wallet insuffisant. " + amountTnd + " TND requis pour l'abonnement " + durationLabel(duration) + ".");
    }

    // 1. Retrait du Cash Wallet (burn) — la transaction apparaîtra dans l'historique (WITHDRAW)
    try {
      burnKey.burn(wallet, amount1e8);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Échec du prélèvement Cash Wallet: " + e.getMessage(), e);
    }

    // 2. Activer/Renouveler Premium en base (dates de validité) et on-chain
    Instant now = Instant.now();
    long durationDays = durationDays(duration);
    user.setPremium(true);
    // En renouvellement : prolonger depuis la fin actuelle si pas expiré, sinon depuis maintenant
    Instant base = user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(now)
        ? user.getPremiumExpiresAt()
        : now;
    user.setPremiumStartAt(user.getPremiumStartAt() != null && user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(now)
        ? user.getPremiumStartAt()
        : now);
    user.setPremiumExpiresAt(base.plus(durationDays, ChronoUnit.DAYS));
    user.setPremiumDuration(durationLabel(duration));
    userRepo.save(user);

    try {
      registryWrite.setSubscriptionActive(wallet, true);
      sciPush.pushForWallet(wallet);
    } catch (Exception e) {
      // Rollback premium en base si la synchro on-chain échoue
      // Note: le burn est déjà fait, on ne peut pas le reverter facilement
      user.setPremium(false);
      userRepo.save(user);
      throw new IllegalStateException("Erreur synchro blockchain: " + e.getMessage());
    }

    notificationService.create(userId, Type.SECURITY,
        "Abonnement Premium activé",
        "Votre abonnement Premium " + durationLabel(duration) + " est actif. " + amountTnd + " TND ont été débités de votre Cash Wallet.",
        Priority.MEDIUM);

    return "Abonnement Premium " + durationLabel(duration) + " activé. " + amountTnd + " TND débités de votre Cash Wallet.";
  }

  /**
   * Retourne les tarifs d'abonnement pour l'utilisateur (trimestriel, semestriel, annuel).
   * -1 = non disponible (tier BRONZE).
   */
  public java.util.Map<String, Integer> getSubscriptionPricesForUser(String userId) {
    AppUser user = userRepo.findById(userId).orElse(null);
    if (user == null) return java.util.Map.of("trimestriel", -1, "semestriel", -1, "annuel", -1);
    String wallet = user.getWalletAddress();
    if (wallet == null || wallet.isBlank() || !wallet.startsWith("0x")) {
      return java.util.Map.of("trimestriel", -1, "semestriel", -1, "annuel", -1);
    }
    var profile = blockchainRead.investorProfile(wallet);
    int feeLevel = profile != null ? profile.feeLevel() : 0;
    if (feeLevel < 0 || feeLevel > 4) feeLevel = 0;
    return java.util.Map.of(
        "trimestriel", getSubscriptionAmountTnd(feeLevel, SubscriptionDuration.TRIMESTRIEL),
        "semestriel", getSubscriptionAmountTnd(feeLevel, SubscriptionDuration.SEMESTRIEL),
        "annuel", getSubscriptionAmountTnd(feeLevel, SubscriptionDuration.ANNUEL)
    );
  }

  /** Montant TND selon tier et durée (Spec v4.7, Table 1). -1 = non disponible. */
  public int getSubscriptionAmountTnd(int feeLevel, SubscriptionDuration duration) {
    if (feeLevel < 0 || feeLevel >= SpecFinancieresV47.TIER_SUBSCRIPTION_TND.length) {
      return -1;
    }
    int[] row = SpecFinancieresV47.TIER_SUBSCRIPTION_TND[feeLevel];
    if (duration.index < 0 || duration.index >= row.length) {
      return -1;
    }
    return row[duration.index];
  }

  private static String durationLabel(SubscriptionDuration d) {
    return switch (d) {
      case TRIMESTRIEL -> "trimestriel";
      case SEMESTRIEL -> "semestriel";
      case ANNUEL -> "annuel";
    };
  }

  /** Durée en jours pour le calcul d'expiration. */
  private static long durationDays(SubscriptionDuration d) {
    return switch (d) {
      case TRIMESTRIEL -> 90;
      case SEMESTRIEL -> 180;
      case ANNUEL -> 365;
    };
  }
}
