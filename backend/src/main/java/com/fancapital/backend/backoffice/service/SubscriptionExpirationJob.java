package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.model.Notification.Type;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.auth.service.NotificationService;
import com.fancapital.backend.blockchain.service.InvestorRegistryWriteService;
import com.fancapital.backend.blockchain.service.SciScorePushService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Job de relance automatique pour les abonnements Premium expirant bientôt.
 * Envoie des notifications aux utilisateurs pour éviter la suspension du service AST.
 * Désactive aussi les abonnements expirés (premium=false + on-chain).
 */
@Service
public class SubscriptionExpirationJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationJob.class);
  private static final int EXPIRING_SOON_DAYS = 14;

  private final AppUserRepository userRepo;
  private final NotificationService notificationService;
  private final InvestorRegistryWriteService registryWrite;
  private final SciScorePushService sciPush;

  public SubscriptionExpirationJob(AppUserRepository userRepo, NotificationService notificationService,
      InvestorRegistryWriteService registryWrite, SciScorePushService sciPush) {
    this.userRepo = userRepo;
    this.notificationService = notificationService;
    this.registryWrite = registryWrite;
    this.sciPush = sciPush;
  }

  /** Exécution quotidienne à 9h : envoi des relances pour les abonnements expirant sous 14 jours. */
  @Scheduled(cron = "${backoffice.subscription-reminder-cron:0 0 9 * * ?}") // 9h par défaut
  public void sendExpirationReminders() {
    try {
      Instant now = Instant.now();
      Instant before = now.plus(EXPIRING_SOON_DAYS, ChronoUnit.DAYS);
      List<com.fancapital.backend.auth.model.AppUser> expiring = userRepo.findPremiumExpiringBetween(now, before);

      int sent = 0;
      for (var u : expiring) {
        long daysLeft = ChronoUnit.DAYS.between(now, u.getPremiumExpiresAt());
        if (daysLeft < 0) continue;

        String msg = daysLeft == 0
            ? "Votre abonnement Premium expire aujourd'hui. Renouvelez pour conserver l'accès à l'Avance sur Titres (AST) et aux services P2P."
            : String.format(
                "Votre abonnement Premium expire dans %d jour%s. Renouvelez pour conserver l'accès à l'Avance sur Titres (AST) et aux services P2P.",
                daysLeft, daysLeft > 1 ? "s" : "");

        try {
          notificationService.create(u.getId(), Type.SECURITY, "Abonnement Premium — expiration proche", msg, Priority.HIGH);
          sent++;
        } catch (Exception e) {
          log.warn("SubscriptionExpirationJob: failed to notify user {}: {}", u.getEmail(), e.getMessage());
        }
      }

      if (sent > 0) {
        log.info("SubscriptionExpirationJob: sent {} expiration reminder(s)", sent);
      }

      // Désactiver les abonnements expirés
      deactivateExpiredSubscriptions(now);
    } catch (Exception e) {
      log.error("SubscriptionExpirationJob: error: {}", e.getMessage(), e);
    }
  }

  private void deactivateExpiredSubscriptions(Instant now) {
    List<AppUser> allPremium = userRepo.findByPremiumTrue();
    int deactivated = 0;
    for (AppUser u : allPremium) {
      if (u.getPremiumExpiresAt() != null && u.getPremiumExpiresAt().isBefore(now)) {
        try {
          u.setPremium(false);
          userRepo.save(u);
          String wallet = u.getWalletAddress();
          if (wallet != null && !wallet.isBlank()) {
            registryWrite.setSubscriptionActive(wallet, false);
            sciPush.pushForWallet(wallet);
          }
          notificationService.create(u.getId(), Type.SECURITY,
              "Abonnement Premium expiré",
              "Votre abonnement Premium a expiré. Renouvelez pour retrouver l'accès à l'AST et aux services P2P.",
              Priority.HIGH);
          deactivated++;
        } catch (Exception e) {
          log.warn("SubscriptionExpirationJob: failed to deactivate user {}: {}", u.getEmail(), e.getMessage());
        }
      }
    }
    if (deactivated > 0) {
      log.info("SubscriptionExpirationJob: deactivated {} expired subscription(s)", deactivated);
    }
  }
}
