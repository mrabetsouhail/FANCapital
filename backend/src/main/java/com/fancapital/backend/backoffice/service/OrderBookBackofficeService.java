package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.model.OrderBookBackofficeDtos;
import com.fancapital.backend.blockchain.model.OrderBookDtos.Order;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderStatus;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.OrderBookService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service backoffice pour la surveillance du Order Book P2P & Fallback.
 * - Ordres en attente (PENDING) avec TTL
 * - Historique des ordres matchés en P2P
 * - Audit des bascules (fallback vers la piscine)
 */
@Service
public class OrderBookBackofficeService {

  private final OrderBookService orderBookService;
  private final DeploymentRegistry registry;

  public OrderBookBackofficeService(OrderBookService orderBookService, DeploymentRegistry registry) {
    this.orderBookService = orderBookService;
    this.registry = registry;
  }

  /** Ordres PENDING avec Time-to-Live. */
  public OrderBookBackofficeDtos.PendingOrdersResponse listPendingOrders() {
    List<Order> all = orderBookService.getAllOrdersForBackoffice();
    long now = Instant.now().getEpochSecond();
    List<OrderBookBackofficeDtos.OrderRow> rows = all.stream()
        .filter(o -> o.status() == OrderStatus.PENDING)
        .sorted(Comparator.comparingLong(Order::deadline))
        .map(o -> toOrderRow(o, now, null))
        .toList();
    return new OrderBookBackofficeDtos.PendingOrdersResponse(rows, rows.size());
  }

  /** Historique des ordres matchés en P2P (partiel ou total). */
  public OrderBookBackofficeDtos.MatchedOrdersResponse listMatchedOrders() {
    List<Order> all = orderBookService.getAllOrdersForBackoffice();
    long now = Instant.now().getEpochSecond();
    List<OrderBookBackofficeDtos.OrderRow> rows = all.stream()
        .filter(o -> (o.status() == OrderStatus.MATCHED || o.status() == OrderStatus.SETTLED)
            && o.matchedOrderId() != null && !o.matchedOrderId().isBlank())
        .sorted(Comparator.comparing(Order::createdAt).reversed())
        .map(o -> toOrderRow(o, now, null))
        .toList();
    return new OrderBookBackofficeDtos.MatchedOrdersResponse(rows, rows.size());
  }

  /** Audit des ordres ayant basculé vers la piscine (expirés → fallback). */
  public OrderBookBackofficeDtos.FallbackAuditResponse listFallbackAudit() {
    List<Order> all = orderBookService.getAllOrdersForBackoffice();
    long now = Instant.now().getEpochSecond();
    List<OrderBookBackofficeDtos.OrderRow> rows = all.stream()
        .filter(o -> isFallbackOrder(o))
        .sorted(Comparator.comparing(Order::createdAt).reversed())
        .map(o -> toOrderRow(o, now, isFallbackSuccess(o)))
        .toList();
    return new OrderBookBackofficeDtos.FallbackAuditResponse(rows, rows.size());
  }

  private boolean isFallbackOrder(Order o) {
    if (o.status() == OrderStatus.EXPIRED) return true;
    if (o.status() == OrderStatus.SETTLED && (o.matchedOrderId() == null || o.matchedOrderId().isBlank())) return true;
    return false;
  }

  private Boolean isFallbackSuccess(Order o) {
    if (o.status() == OrderStatus.EXPIRED) return false;
    if (o.status() == OrderStatus.SETTLED && (o.matchedOrderId() == null || o.matchedOrderId().isBlank())) return true;
    return null;
  }

  private OrderBookBackofficeDtos.OrderRow toOrderRow(Order o, long now, Boolean fallbackSuccess) {
    Long ttL = o.status() == OrderStatus.PENDING ? Math.max(0, o.deadline() - now) : null;
    String symbol = registry.findByToken(o.token()).map(f -> f.symbol() != null ? f.symbol() : f.name()).orElse("CPEF");
    return new OrderBookBackofficeDtos.OrderRow(
        o.orderId(),
        o.maker(),
        o.side().name(),
        o.token(),
        symbol,
        o.tokenAmount(),
        o.pricePerToken(),
        o.status().name(),
        o.createdAt(),
        o.deadline(),
        ttL,
        o.matchedOrderId(),
        o.settlementTxHash(),
        o.filledTokenAmount(),
        fallbackSuccess != null ? fallbackSuccess : isFallbackSuccess(o)
    );
  }
}
