package com.fancapital.backend.blockchain.controller;

import com.fancapital.backend.blockchain.model.OrderBookDtos;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderSide;
import com.fancapital.backend.blockchain.model.OrderBookDtos.SubmitOrderRequest;
import com.fancapital.backend.blockchain.service.OrderBookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blockchain/p2p")
public class OrderBookController {
  private static final String ETH_ADDRESS_RX = "^0x[a-fA-F0-9]{40}$";

  private final OrderBookService orderBookService;

  public OrderBookController(OrderBookService orderBookService) {
    this.orderBookService = orderBookService;
  }

  /**
   * Soumet un nouvel ordre P2P.
   * L'utilisateur connecté (via JWT) est automatiquement utilisé comme maker.
   * L'ordre sera automatiquement matché s'il y a un ordre compatible dans le book.
   */
  @PostMapping("/order")
  public OrderBookDtos.SubmitOrderResponse submitOrder(@Valid @RequestBody SubmitOrderRequest req) {
    String userId = getCurrentUserId();
    return orderBookService.submitOrder(req, userId);
  }

  /**
   * Liste les ordres ouverts (optionnellement filtrés par token et side).
   */
  @GetMapping("/orders")
  public OrderBookDtos.OrdersListResponse listOrders(
      @RequestParam(required = false) @Pattern(regexp = ETH_ADDRESS_RX) String token,
      @RequestParam(required = false) String side // "buy" or "sell"
  ) {
    OrderSide orderSide = null;
    if (side != null && !side.isBlank()) {
      String s = side.trim().toLowerCase();
      if ("buy".equals(s)) orderSide = OrderSide.BUY;
      else if ("sell".equals(s)) orderSide = OrderSide.SELL;
    }
    return orderBookService.listOrders(token, orderSide);
  }

  /**
   * Récupère un ordre par son ID.
   */
  @GetMapping("/order/{orderId}")
  public ResponseEntity<OrderBookDtos.Order> getOrder(@PathVariable String orderId) {
    Optional<OrderBookDtos.Order> order = orderBookService.getOrderById(orderId);
    return order.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Probabilité de matching (heuristique basée sur l'historique des ordres ouverts).
   * Pour une période choisie, informe l'utilisateur de la probabilité estimée.
   */
  @GetMapping("/matching-probability")
  public OrderBookDtos.MatchingProbabilityResponse getMatchingProbability(
      @RequestParam(required = false) @Pattern(regexp = ETH_ADDRESS_RX) String token,
      @RequestParam(required = false, defaultValue = "24") int periodHours
  ) {
    return orderBookService.getMatchingProbability(token, periodHours);
  }

  /**
   * Montants réservés par les ordres P2P en attente (cash pour achats, tokens pour ventes).
   * Ces montants ne sont pas utilisables pour d'autres ordres jusqu'à exécution/annulation.
   */
  @GetMapping("/reservations")
  public OrderBookDtos.P2PReservationsResponse getReservations(
      @RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user
  ) {
    return orderBookService.getReservations(user);
  }

  /**
   * Annule un ordre (seulement si PENDING).
   * L'utilisateur connecté doit être le maker de l'ordre.
   */
  @DeleteMapping("/order/{orderId}")
  public OrderBookDtos.CancelOrderResponse cancelOrder(@PathVariable String orderId) {
    String userId = getCurrentUserId();
    return orderBookService.cancelOrder(orderId, userId);
  }

  private String getCurrentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new IllegalArgumentException("Unauthorized: Please log in to submit P2P orders");
    }
    String userId = String.valueOf(auth.getPrincipal());
    if (userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)) {
      throw new IllegalArgumentException("Unauthorized: Please log in to submit P2P orders");
    }
    return userId;
  }
}
