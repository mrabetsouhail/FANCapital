package com.fancapital.backend.blockchain.scheduler;

import com.fancapital.backend.blockchain.service.OrderBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Surveille les ordres P2P expirés et déclenche le fallback vers la piscine de liquidité.
 * Conformément au modèle P2P Hybrid-Order-Book.
 */
@Component
public class OrderScheduler {
  private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

  private final OrderBookService orderBookService;

  public OrderScheduler(OrderBookService orderBookService) {
    this.orderBookService = orderBookService;
  }

  /** Exécution toutes les minutes : recherche des ordres PENDING expirés et fallback. */
  @Scheduled(fixedRate = 60_000) // 1 minute
  public void processExpiredOrders() {
    try {
      orderBookService.processExpiredOrders();
    } catch (Exception e) {
      log.error("OrderScheduler: error processing expired orders: {}", e.getMessage(), e);
    }
  }
}
