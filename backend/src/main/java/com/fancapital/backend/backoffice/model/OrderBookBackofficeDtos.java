package com.fancapital.backend.backoffice.model;

import java.time.Instant;

/**
 * DTOs pour le backoffice Order Book P2P & Fallback.
 */
public class OrderBookBackofficeDtos {

  /** Ordre avec métadonnées enrichies pour le backoffice. */
  public record OrderRow(
      String orderId,
      String maker,
      String side,           // BUY / SELL
      String token,
      String tokenSymbol,    // Atlas / Didon
      String tokenAmount,
      String pricePerToken,
      String status,         // PENDING, MATCHED, SETTLED, CANCELLED, EXPIRED
      Instant createdAt,
      long deadline,
      Long ttLSeconds,       // secondes restantes jusqu'à expiration (null si non PENDING)
      String matchedOrderId,
      String settlementTxHash,
      String filledTokenAmount,
      Boolean fallbackSuccess   // true=exécuté via piscine, false=fallback échoué (EXPIRED), null=non fallback
  ) {}

  public record PendingOrdersResponse(java.util.List<OrderRow> orders, int totalCount) {}

  public record MatchedOrdersResponse(java.util.List<OrderRow> orders, int totalCount) {}

  public record FallbackAuditResponse(java.util.List<OrderRow> orders, int totalCount) {}
}
