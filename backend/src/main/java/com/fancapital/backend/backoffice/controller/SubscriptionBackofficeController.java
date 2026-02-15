package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.model.SubscriptionBackofficeDtos;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.SubscriptionBackofficeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur backoffice pour la Gestion des Abonnements Premium.
 * - Moniteur de validité (Trimestriel, Semestriel, Annuel)
 * - Relances : abonnements expirant bientôt
 */
@RestController
@RequestMapping("/api/backoffice/subscriptions")
public class SubscriptionBackofficeController {

  private final SubscriptionBackofficeService subscriptionService;
  private final BackofficeAuthzService authz;

  public SubscriptionBackofficeController(SubscriptionBackofficeService subscriptionService,
      BackofficeAuthzService authz) {
    this.subscriptionService = subscriptionService;
    this.authz = authz;
  }

  /** Vue d'ensemble des abonnements actifs avec validité. */
  @GetMapping("/monitor")
  public SubscriptionBackofficeDtos.SubscriptionsMonitorResponse monitor() {
    authz.requireAuditRead();
    return subscriptionService.listActiveSubscriptions();
  }

  /** Abonnements expirant sous 14 jours (pour relances). */
  @GetMapping("/expiring-soon")
  public SubscriptionBackofficeDtos.ExpiringSubscriptionsResponse expiringSoon() {
    authz.requireAuditRead();
    return subscriptionService.listExpiringSoon();
  }
}
