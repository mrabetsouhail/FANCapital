package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.model.OrderBookDtos;
import com.fancapital.backend.blockchain.model.OrderBookDtos.Order;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderSide;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderStatus;
import com.fancapital.backend.blockchain.model.OrderBookDtos.SubmitOrderRequest;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Service de gestion de l'order book P2P en mémoire.
 * 
 * Note: Pour la production, il faudrait utiliser une base de données persistante
 * et potentiellement un système de queue (Redis, RabbitMQ) pour le matching distribué.
 */
@Service
public class OrderBookService {
  // Order book en mémoire: token -> side -> list of orders (sorted by price, then time)
  private final Map<String, Map<OrderSide, List<Order>>> orderBook = new ConcurrentHashMap<>();
  
  // Index par orderId pour accès rapide
  private final Map<String, Order> ordersById = new ConcurrentHashMap<>();

  private final P2PExchangeWriteService p2pService;
  private final AppUserRepository userRepo;
  private final WaasUserWalletService waasWallets;

  public OrderBookService(
      P2PExchangeWriteService p2pService,
      AppUserRepository userRepo,
      WaasUserWalletService waasWallets
  ) {
    this.p2pService = p2pService;
    this.userRepo = userRepo;
    this.waasWallets = waasWallets;
  }

  /**
   * Soumet un nouvel ordre et tente de le matcher immédiatement.
   * 
   * @param req Requête de soumission d'ordre (maker sera ignoré, remplacé par userId)
   * @param userId ID de l'utilisateur connecté (récupéré du JWT)
   * @return Réponse avec l'ordre créé et potentiellement matché
   */
  public OrderBookDtos.SubmitOrderResponse submitOrder(SubmitOrderRequest req, String userId) {
    // Récupérer l'utilisateur et son wallet WaaS
    AppUser user = userRepo.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    
    String walletAddress = user.getWalletAddress();
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new IllegalStateException("User has no wallet. Wallet is created automatically after KYC Level 1 validation.");
    }

    // Valider et normaliser les paramètres
    OrderSide side = parseSide(req.side());
    String token = req.token().toLowerCase();
    // Valider les montants (mais on garde les strings pour le matching)
    parseUint(req.tokenAmount(), "tokenAmount");
    parseUint(req.pricePerToken(), "pricePerToken");
    
    String nonce = req.nonce() != null && !req.nonce().isBlank() 
        ? req.nonce() 
        : generateNonce();
    
    long deadline = req.deadline() != null && !req.deadline().isBlank()
        ? Long.parseLong(req.deadline())
        : Instant.now().getEpochSecond() + 3600; // +1 heure par défaut

    // Vérifier que l'ordre n'est pas expiré
    if (deadline <= Instant.now().getEpochSecond()) {
      throw new IllegalArgumentException("Order deadline must be in the future");
    }

    // Créer l'ordre avec le wallet de l'utilisateur (WaaS)
    String orderId = UUID.randomUUID().toString();
    Order newOrder = new Order(
        orderId,
        walletAddress.toLowerCase(), // Utiliser le wallet WaaS de l'utilisateur
        side,
        token,
        req.tokenAmount(),
        req.pricePerToken(),
        nonce,
        deadline,
        OrderStatus.PENDING,
        Instant.now(),
        null, // Pas de signature nécessaire (WaaS géré par la plateforme)
        null, // matchedOrderId
        null  // settlementTxHash
    );

    // Ajouter au book
    addOrderToBook(newOrder);
    ordersById.put(orderId, newOrder);

    // Tenter le matching immédiat
    Optional<Order> matched = tryMatchOrder(newOrder);
    
    if (matched.isPresent()) {
      Order matchedOrder = matched.get();
      // Mettre à jour les statuts
      Order updatedNewOrder = new Order(
          newOrder.orderId(),
          newOrder.maker(),
          newOrder.side(),
          newOrder.token(),
          newOrder.tokenAmount(),
          newOrder.pricePerToken(),
          newOrder.nonce(),
          newOrder.deadline(),
          OrderStatus.MATCHED,
          newOrder.createdAt(),
          newOrder.signature(),
          matchedOrder.orderId(),
          null
      );
      
      Order updatedMatchedOrder = new Order(
          matchedOrder.orderId(),
          matchedOrder.maker(),
          matchedOrder.side(),
          matchedOrder.token(),
          matchedOrder.tokenAmount(),
          matchedOrder.pricePerToken(),
          matchedOrder.nonce(),
          matchedOrder.deadline(),
          OrderStatus.MATCHED,
          matchedOrder.createdAt(),
          matchedOrder.signature(),
          newOrder.orderId(),
          null
      );

      // Mettre à jour dans le book
      updateOrderInBook(updatedNewOrder);
      updateOrderInBook(updatedMatchedOrder);
      ordersById.put(updatedNewOrder.orderId(), updatedNewOrder);
      ordersById.put(updatedMatchedOrder.orderId(), updatedMatchedOrder);

      // Exécuter le settlement sur la blockchain
      try {
        String buyer = side == OrderSide.BUY ? newOrder.maker() : matchedOrder.maker();
        String seller = side == OrderSide.SELL ? newOrder.maker() : matchedOrder.maker();
        
        String txHash = p2pService.settle(
            new com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest(
                token,
                seller,
                buyer,
                req.tokenAmount(),
                req.pricePerToken(),
                null, // maker (optional)
                null, // side (optional)
                null, // nonce (optional)
                null, // deadline (optional)
                null  // signature (optional)
            )
        );

        // Mettre à jour avec le txHash
        Order settledNewOrder = new Order(
            updatedNewOrder.orderId(),
            updatedNewOrder.maker(),
            updatedNewOrder.side(),
            updatedNewOrder.token(),
            updatedNewOrder.tokenAmount(),
            updatedNewOrder.pricePerToken(),
            updatedNewOrder.nonce(),
            updatedNewOrder.deadline(),
            OrderStatus.SETTLED,
            updatedNewOrder.createdAt(),
            updatedNewOrder.signature(),
            updatedNewOrder.matchedOrderId(),
            txHash
        );
        
        Order settledMatchedOrder = new Order(
            updatedMatchedOrder.orderId(),
            updatedMatchedOrder.maker(),
            updatedMatchedOrder.side(),
            updatedMatchedOrder.token(),
            updatedMatchedOrder.tokenAmount(),
            updatedMatchedOrder.pricePerToken(),
            updatedMatchedOrder.nonce(),
            updatedMatchedOrder.deadline(),
            OrderStatus.SETTLED,
            updatedMatchedOrder.createdAt(),
            updatedMatchedOrder.signature(),
            updatedMatchedOrder.matchedOrderId(),
            txHash
        );

        updateOrderInBook(settledNewOrder);
        updateOrderInBook(settledMatchedOrder);
        ordersById.put(settledNewOrder.orderId(), settledNewOrder);
        ordersById.put(settledMatchedOrder.orderId(), settledMatchedOrder);

        return new OrderBookDtos.SubmitOrderResponse(
            orderId,
            OrderStatus.SETTLED,
            "Order matched and settled on blockchain",
            settledMatchedOrder
        );
      } catch (Exception e) {
        // En cas d'erreur de settlement, les ordres restent en MATCHED
        return new OrderBookDtos.SubmitOrderResponse(
            orderId,
            OrderStatus.MATCHED,
            "Order matched but settlement failed: " + e.getMessage(),
            updatedMatchedOrder
        );
      }
    }

    // Pas de matching immédiat
    return new OrderBookDtos.SubmitOrderResponse(
        orderId,
        OrderStatus.PENDING,
        "Order submitted and added to order book",
        null
    );
  }

  /**
   * Liste les ordres ouverts pour un token donné (optionnel).
   */
  public OrderBookDtos.OrdersListResponse listOrders(String token, OrderSide side) {
    List<Order> allOrders = new ArrayList<>();
    
    if (token != null && !token.isBlank()) {
      String tokenLower = token.toLowerCase();
      Map<OrderSide, List<Order>> tokenBook = orderBook.get(tokenLower);
      if (tokenBook != null) {
        if (side != null) {
          List<Order> orders = tokenBook.get(side);
          if (orders != null) {
            allOrders.addAll(orders.stream()
                .filter(o -> o.status() == OrderStatus.PENDING)
                .toList());
          }
        } else {
          tokenBook.values().forEach(orders -> 
              allOrders.addAll(orders.stream()
                  .filter(o -> o.status() == OrderStatus.PENDING)
                  .toList())
          );
        }
      }
    } else {
      // Tous les ordres
      orderBook.values().forEach(tokenBook -> 
          tokenBook.values().forEach(orders -> 
              allOrders.addAll(orders.stream()
                  .filter(o -> o.status() == OrderStatus.PENDING)
                  .toList())
          )
      );
    }

    return new OrderBookDtos.OrdersListResponse(allOrders, allOrders.size());
  }

  /**
   * Récupère un ordre par son ID.
   */
  public Optional<Order> getOrderById(String orderId) {
    return Optional.ofNullable(ordersById.get(orderId));
  }

  /**
   * Annule un ordre (seulement si PENDING).
   * 
   * @param orderId ID de l'ordre à annuler
   * @param userId ID de l'utilisateur connecté (vérifié contre le maker de l'ordre)
   */
  public OrderBookDtos.CancelOrderResponse cancelOrder(String orderId, String userId) {
    Order order = ordersById.get(orderId);
    if (order == null) {
      return new OrderBookDtos.CancelOrderResponse(orderId, false, "Order not found");
    }

    // Vérifier que l'utilisateur est bien le maker de l'ordre
    AppUser user = userRepo.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    String walletAddress = user.getWalletAddress();
    if (walletAddress == null || !walletAddress.equalsIgnoreCase(order.maker())) {
      return new OrderBookDtos.CancelOrderResponse(orderId, false, "Only order maker can cancel");
    }

    if (order.status() != OrderStatus.PENDING) {
      return new OrderBookDtos.CancelOrderResponse(orderId, false, "Only PENDING orders can be cancelled");
    }

    // Retirer du book et marquer comme annulé
    removeOrderFromBook(order);
    Order cancelled = new Order(
        order.orderId(),
        order.maker(),
        order.side(),
        order.token(),
        order.tokenAmount(),
        order.pricePerToken(),
        order.nonce(),
        order.deadline(),
        OrderStatus.CANCELLED,
        order.createdAt(),
        order.signature(),
        order.matchedOrderId(),
        order.settlementTxHash()
    );
    ordersById.put(orderId, cancelled);

    return new OrderBookDtos.CancelOrderResponse(orderId, true, "Order cancelled");
  }

  // ========== Méthodes privées ==========

  private Optional<Order> tryMatchOrder(Order newOrder) {
    String token = newOrder.token();
    OrderSide oppositeSide = newOrder.side() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    
    Map<OrderSide, List<Order>> tokenBook = orderBook.get(token);
    if (tokenBook == null) return Optional.empty();
    
    List<Order> oppositeOrders = tokenBook.get(oppositeSide);
    if (oppositeOrders == null || oppositeOrders.isEmpty()) return Optional.empty();

    BigInteger newPrice = new BigInteger(newOrder.pricePerToken());
    BigInteger newAmount = new BigInteger(newOrder.tokenAmount());

    // Chercher un ordre compatible (même prix ou meilleur, et montant compatible)
    for (Order opposite : oppositeOrders) {
      if (opposite.status() != OrderStatus.PENDING) continue;
      if (opposite.maker().equalsIgnoreCase(newOrder.maker())) continue; // Pas de self-trade
      
      BigInteger oppositePrice = new BigInteger(opposite.pricePerToken());
      BigInteger oppositeAmount = new BigInteger(opposite.tokenAmount());

      // Vérifier la compatibilité des prix
      boolean priceMatch = false;
      if (newOrder.side() == OrderSide.BUY) {
        // Pour un BUY, on accepte si le prix du SELL est <= au prix du BUY
        priceMatch = oppositePrice.compareTo(newPrice) <= 0;
      } else {
        // Pour un SELL, on accepte si le prix du BUY est >= au prix du SELL
        priceMatch = oppositePrice.compareTo(newPrice) >= 0;
      }

      if (priceMatch) {
        // Pour simplifier, on match seulement si les montants sont exactement égaux
        // (Dans un système complet, on gérerait le partial matching)
        if (oppositeAmount.equals(newAmount)) {
          return Optional.of(opposite);
        }
      }
    }

    return Optional.empty();
  }

  private void addOrderToBook(Order order) {
    orderBook.computeIfAbsent(order.token(), k -> new ConcurrentHashMap<>())
        .computeIfAbsent(order.side(), k -> new ArrayList<>())
        .add(order);
    
    // Trier par prix (meilleur prix en premier), puis par temps (plus ancien en premier)
    sortOrderBook(order.token(), order.side());
  }

  private void updateOrderInBook(Order order) {
    Map<OrderSide, List<Order>> tokenBook = orderBook.get(order.token());
    if (tokenBook == null) return;
    
    List<Order> orders = tokenBook.get(order.side());
    if (orders == null) return;
    
    for (int i = 0; i < orders.size(); i++) {
      if (orders.get(i).orderId().equals(order.orderId())) {
        orders.set(i, order);
        break;
      }
    }
  }

  private void removeOrderFromBook(Order order) {
    Map<OrderSide, List<Order>> tokenBook = orderBook.get(order.token());
    if (tokenBook == null) return;
    
    List<Order> orders = tokenBook.get(order.side());
    if (orders == null) return;
    
    orders.removeIf(o -> o.orderId().equals(order.orderId()));
  }

  private void sortOrderBook(String token, OrderSide side) {
    Map<OrderSide, List<Order>> tokenBook = orderBook.get(token);
    if (tokenBook == null) return;
    
    List<Order> orders = tokenBook.get(side);
    if (orders == null) return;
    
    orders.sort(Comparator
        .<Order>comparingInt(o -> {
          BigInteger price = new BigInteger(o.pricePerToken());
          // Pour BUY: prix décroissant (meilleur prix = plus élevé)
          // Pour SELL: prix croissant (meilleur prix = plus bas)
          return side == OrderSide.BUY ? -price.compareTo(BigInteger.ZERO) : price.compareTo(BigInteger.ZERO);
        })
        .thenComparing(o -> o.createdAt()) // Plus ancien en premier
    );
  }

  private OrderSide parseSide(String side) {
    if (side == null || side.isBlank()) {
      throw new IllegalArgumentException("side is required (buy or sell)");
    }
    String s = side.trim().toLowerCase();
    if ("buy".equals(s)) return OrderSide.BUY;
    if ("sell".equals(s)) return OrderSide.SELL;
    throw new IllegalArgumentException("side must be 'buy' or 'sell', got: " + side);
  }

  private BigInteger parseUint(String raw, String field) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException(field + " is required");
    try {
      BigInteger v = new BigInteger(raw.trim());
      if (v.signum() < 0) throw new IllegalArgumentException(field + " must be >= 0");
      return v;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(field + " must be an integer string");
    }
  }

  private String generateNonce() {
    return String.valueOf(System.currentTimeMillis() + (long)(Math.random() * 1000000));
  }
}
