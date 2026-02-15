package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.model.SubscriptionBackofficeDtos;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service backoffice pour la gestion des abonnements Premium.
 * - Moniteur de validité (Trimestriel, Semestriel, Annuel)
 * - Liste des abonnements expirant bientôt (relances)
 */
@Service
public class SubscriptionBackofficeService {

  private static final int EXPIRING_SOON_DAYS = 14;

  private final AppUserRepository userRepo;

  public SubscriptionBackofficeService(AppUserRepository userRepo) {
    this.userRepo = userRepo;
  }

  /**
   * Vue d'ensemble des abonnements actifs avec répartition par durée.
   */
  public SubscriptionBackofficeDtos.SubscriptionsMonitorResponse listActiveSubscriptions() {
    List<AppUser> premiumUsers = userRepo.findByPremiumTrue();
    Instant now = Instant.now();

    List<SubscriptionBackofficeDtos.SubscriptionRow> rows = new ArrayList<>();
    int trimCount = 0;
    int semCount = 0;
    int annCount = 0;
    int expiringSoon = 0;

    for (AppUser u : premiumUsers) {
      String duration = u.getPremiumDuration() != null ? u.getPremiumDuration() : "—";
      if ("trimestriel".equalsIgnoreCase(duration)) trimCount++;
      else if ("semestriel".equalsIgnoreCase(duration)) semCount++;
      else if ("annuel".equalsIgnoreCase(duration)) annCount++;

      Instant expiresAt = u.getPremiumExpiresAt();
      Long daysRemaining = expiresAt != null
          ? ChronoUnit.DAYS.between(now, expiresAt)
          : null;

      boolean expiringSoonFlag = expiresAt != null
          && daysRemaining != null
          && daysRemaining >= 0
          && daysRemaining <= EXPIRING_SOON_DAYS;
      if (expiringSoonFlag) expiringSoon++;

      rows.add(new SubscriptionBackofficeDtos.SubscriptionRow(
          u.getId(),
          u.getEmail(),
          u.getNom() != null && u.getPrenom() != null ? u.getPrenom() + " " + u.getNom() : u.getDenominationSociale() != null ? u.getDenominationSociale() : u.getEmail(),
          u.getWalletAddress() != null ? u.getWalletAddress() : "—",
          duration,
          u.getPremiumStartAt(),
          expiresAt,
          daysRemaining,
          expiringSoonFlag
      ));
    }

    // Trier par date d'expiration (les plus urgents en premier)
    rows.sort((a, b) -> {
      if (a.expiresAt() == null && b.expiresAt() == null) return 0;
      if (a.expiresAt() == null) return 1;
      if (b.expiresAt() == null) return -1;
      return a.expiresAt().compareTo(b.expiresAt());
    });

    return new SubscriptionBackofficeDtos.SubscriptionsMonitorResponse(
        rows,
        rows.size(),
        trimCount,
        semCount,
        annCount,
        expiringSoon
    );
  }

  /**
   * Abonnements expirant dans les X prochains jours (pour relances).
   */
  public SubscriptionBackofficeDtos.ExpiringSubscriptionsResponse listExpiringSoon() {
    Instant now = Instant.now();
    Instant before = now.plus(EXPIRING_SOON_DAYS, ChronoUnit.DAYS);
    List<AppUser> expiring = userRepo.findPremiumExpiringBetween(now, before);

    List<SubscriptionBackofficeDtos.ExpiringSubscriptionRow> rows = expiring.stream()
        .map(u -> new SubscriptionBackofficeDtos.ExpiringSubscriptionRow(
            u.getId(),
            u.getEmail(),
            u.getNom() != null && u.getPrenom() != null ? u.getPrenom() + " " + u.getNom() : u.getDenominationSociale() != null ? u.getDenominationSociale() : u.getEmail(),
            u.getPremiumExpiresAt(),
            ChronoUnit.DAYS.between(now, u.getPremiumExpiresAt()),
            u.getPremiumDuration() != null ? u.getPremiumDuration() : "—"
        ))
        .toList();

    return new SubscriptionBackofficeDtos.ExpiringSubscriptionsResponse(rows, rows.size());
  }
}
