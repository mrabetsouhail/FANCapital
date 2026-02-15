package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.model.OrderBookBackofficeDtos;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.OrderBookBackofficeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur backoffice pour la surveillance du Order Book P2P & Fallback.
 * - Monitoring des ordres en attente (TTL)
 * - Log des ordres matchés en P2P
 * - Audit des bascules vers la piscine
 */
@RestController
@RequestMapping("/api/backoffice/orderbook")
public class OrderBookBackofficeController {

  private final OrderBookBackofficeService orderBookBackofficeService;
  private final BackofficeAuthzService authz;

  public OrderBookBackofficeController(OrderBookBackofficeService orderBookBackofficeService,
      BackofficeAuthzService authz) {
    this.orderBookBackofficeService = orderBookBackofficeService;
    this.authz = authz;
  }

  /** Ordres PENDING avec Time-to-Live. */
  @GetMapping("/pending")
  public OrderBookBackofficeDtos.PendingOrdersResponse listPending() {
    authz.requireAuditRead();
    return orderBookBackofficeService.listPendingOrders();
  }

  /** Historique des ordres matchés partiellement ou totalement en P2P. */
  @GetMapping("/matched")
  public OrderBookBackofficeDtos.MatchedOrdersResponse listMatched() {
    authz.requireAuditRead();
    return orderBookBackofficeService.listMatchedOrders();
  }

  /** Audit des ordres transférés vers la piscine (fallback) après expiration. */
  @GetMapping("/fallback")
  public OrderBookBackofficeDtos.FallbackAuditResponse listFallback() {
    authz.requireAuditRead();
    return orderBookBackofficeService.listFallbackAudit();
  }
}
