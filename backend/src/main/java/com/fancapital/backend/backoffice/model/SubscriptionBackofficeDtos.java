package com.fancapital.backend.backoffice.model;

import java.time.Instant;

/**
 * DTOs pour le backoffice Gestion des Abonnements Premium.
 */
public class SubscriptionBackofficeDtos {

  /** Ligne d'abonnement pour le moniteur de validité. */
  public record SubscriptionRow(
      String userId,
      String email,
      String fullName,
      String walletAddress,
      String duration,          // trimestriel, semestriel, annuel
      Instant startAt,
      Instant expiresAt,
      Long daysRemaining,       // jours restants (négatif si expiré)
      boolean expiringSoon      // true si expiration dans les 14 prochains jours
  ) {}

  public record SubscriptionsMonitorResponse(
      java.util.List<SubscriptionRow> subscriptions,
      int totalCount,
      int trimestrielCount,
      int semestrielCount,
      int annuelCount,
      int expiringSoonCount
  ) {}

  /** Utilisateur à relancer (expiration proche). */
  public record ExpiringSubscriptionRow(
      String userId,
      String email,
      String fullName,
      Instant expiresAt,
      long daysRemaining,
      String duration
  ) {}

  public record ExpiringSubscriptionsResponse(
      java.util.List<ExpiringSubscriptionRow> subscriptions,
      int totalCount
  ) {}
}
