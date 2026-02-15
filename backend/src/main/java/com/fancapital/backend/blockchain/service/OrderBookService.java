package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.model.OrderBookDtos;
import com.fancapital.backend.blockchain.model.OrderBookDtos.Order;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderSide;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderStatus;
import com.fancapital.backend.blockchain.model.OrderBookDtos.SubmitOrderRequest;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioPosition;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

  /** P2P fee bps (avant TVA) par tier - aligné avec P2PExchange.sol */
  private static final int[] P2P_FEE_BPS = {80, 75, 70, 60, 50};
  private static final int VAT_BPS = 1_900;
  private static final int BPS = 10_000;
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000);

  private final P2PExchangeWriteService p2pService;
  private final AppUserRepository userRepo;
  private final WaasUserWalletService waasWallets;
  private static final int TIER_SILVER = 1;  // P2P disponible à partir de Silver
  private final OrderFallbackExecutorService fallbackService;
  private final com.fancapital.backend.blockchain.service.DeploymentRegistry registry;
  private final SciScoreService sciScoreService;
  private final BlockchainReadService blockchainRead;

  public OrderBookService(
      P2PExchangeWriteService p2pService,
      AppUserRepository userRepo,
      WaasUserWalletService waasWallets,
      OrderFallbackExecutorService fallbackService,
      com.fancapital.backend.blockchain.service.DeploymentRegistry registry,
      SciScoreService sciScoreService,
      BlockchainReadService blockchainRead
  ) {
    this.p2pService = p2pService;
    this.userRepo = userRepo;
    this.waasWallets = waasWallets;
    this.fallbackService = fallbackService;
    this.registry = registry;
    this.sciScoreService = sciScoreService;
    this.blockchainRead = blockchainRead;
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

    // Hybrid Order Book: Tier Silver + KYC2 requis
    if (user.getKycLevel() < 2) {
      throw new IllegalStateException("L'Order Book P2P requiert un KYC Niveau 2 (Domicile). Validez votre KYC pour accéder.");
    }
    var sciResult = sciScoreService.computeScore(walletAddress);
    if (sciResult.effectiveTier() < TIER_SILVER) {
      throw new IllegalStateException(
          "L'Order Book P2P Hybrid est réservé au tier Silver et supérieurs (Score 16+). Votre tier actuel: " + sciResult.effectiveTier());
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

    // Vérifier que le montant (cash ou tokens) est disponible (non réservé par d'autres ordres P2P)
    validateAvailableBalance(walletAddress, side, token, req.tokenAmount(), req.pricePerToken(), sciResult.effectiveTier());

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
        null, // settlementTxHash
        "0"  // filledTokenAmount
    );

    // Ajouter au book
    addOrderToBook(newOrder);
    ordersById.put(orderId, newOrder);

    // Tenter le matching immédiat (partiel possible : min des montants)
    Optional<MatchResult> matched = tryMatchOrder(newOrder);

    if (matched.isPresent()) {
      MatchResult match = matched.get();
      Order matchedOrder = match.oppositeOrder();
      BigInteger matchedAmount = match.matchedAmount();
      String matchedAmountStr = matchedAmount.toString();
      boolean newOrderFullFill = new BigInteger(newOrder.tokenAmount()).equals(matchedAmount);
      boolean oppositeFullFill = new BigInteger(matchedOrder.tokenAmount()).equals(matchedAmount);

      // Mettre à jour les statuts (filledTokenAmount = portion matchée)
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
          null,
          matchedAmountStr
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
          null,
          matchedAmountStr
      );

      // Si l'ordre opposé est partiellement rempli, retirer du book et ajouter le reliquat
      if (!oppositeFullFill) {
        removeOrderFromBook(matchedOrder);
        BigInteger residualAmount = new BigInteger(matchedOrder.tokenAmount()).subtract(matchedAmount);
        Order residualOrder = new Order(
            UUID.randomUUID().toString(),
            matchedOrder.maker(),
            matchedOrder.side(),
            matchedOrder.token(),
            residualAmount.toString(),
            matchedOrder.pricePerToken(),
            matchedOrder.nonce(),
            matchedOrder.deadline(),
            OrderStatus.PENDING,
            Instant.now(),
            null, null, null, "0"
        );
        addOrderToBook(residualOrder);
        ordersById.put(residualOrder.orderId(), residualOrder);
      } else {
        updateOrderInBook(updatedMatchedOrder);
      }

      // Pour le nouvel ordre : retirer si plein, sinon remplacer par reliquat
      if (newOrderFullFill) {
        removeOrderFromBook(newOrder);
      } else {
        BigInteger residualAmount = new BigInteger(newOrder.tokenAmount()).subtract(matchedAmount);
        Order residualNewOrder = new Order(
            UUID.randomUUID().toString(),
            newOrder.maker(),
            newOrder.side(),
            newOrder.token(),
            residualAmount.toString(),
            newOrder.pricePerToken(),
            newOrder.nonce(),
            newOrder.deadline(),
            OrderStatus.PENDING,
            Instant.now(),
            null, null, null, "0"
        );
        removeOrderFromBook(newOrder);
        addOrderToBook(residualNewOrder);
        ordersById.put(residualNewOrder.orderId(), residualNewOrder);
      }

      ordersById.put(updatedNewOrder.orderId(), updatedNewOrder);
      ordersById.put(updatedMatchedOrder.orderId(), updatedMatchedOrder);

      // Exécuter le settlement sur la blockchain (montant matché uniquement)
      try {
        String buyer = side == OrderSide.BUY ? newOrder.maker() : matchedOrder.maker();
        String seller = side == OrderSide.SELL ? newOrder.maker() : matchedOrder.maker();

        String txHash = p2pService.settle(
            new com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest(
                token,
                seller,
                buyer,
                matchedAmountStr,
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
            txHash,
            updatedNewOrder.filledTokenAmount()
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
            txHash,
            updatedMatchedOrder.filledTokenAmount()
        );

        updateOrderInBook(settledNewOrder);
        updateOrderInBook(settledMatchedOrder);
        ordersById.put(settledNewOrder.orderId(), settledNewOrder);
        ordersById.put(settledMatchedOrder.orderId(), settledMatchedOrder);

        return new OrderBookDtos.SubmitOrderResponse(
            orderId,
            OrderStatus.SETTLED,
            "Order matched and settled on blockchain",
            settledMatchedOrder,
            null
        );
      } catch (Exception e) {
        // En cas d'erreur de settlement, les ordres restent en MATCHED
        return new OrderBookDtos.SubmitOrderResponse(
            orderId,
            OrderStatus.MATCHED,
            "Order matched but settlement failed: " + e.getMessage(),
            updatedMatchedOrder,
            null
        );
      }
    }

    // Pas de matching immédiat
    return new OrderBookDtos.SubmitOrderResponse(
        orderId,
        OrderStatus.PENDING,
        "Order submitted and added to order book",
        null,
        "En cas de non-matching à l'expiration, le reliquat sera exécuté via la piscine de liquidité (spread possiblement différent du P2P)."
    );
  }

  /**
   * Montants réservés par les ordres P2P en attente (PENDING ou MATCHED).
   * Ces montants ne sont pas utilisables pour d'autres ordres tant que l'ordre n'est pas réglé/annulé/expiré.
   */
  public OrderBookDtos.P2PReservationsResponse getReservations(String walletAddress) {
    if (walletAddress == null || walletAddress.isBlank()) {
      return new OrderBookDtos.P2PReservationsResponse("", "0", Map.of());
    }
    String wallet = walletAddress.trim().toLowerCase();
    BigInteger reservedCash = BigInteger.ZERO;
    Map<String, BigInteger> reservedTokens = new HashMap<>();

    for (Order o : ordersById.values()) {
      if (o.status() != OrderStatus.PENDING && o.status() != OrderStatus.MATCHED) continue;
      if (!o.maker().equalsIgnoreCase(wallet)) continue;

      if (o.side() == OrderSide.BUY) {
        BigInteger notional = new BigInteger(o.tokenAmount()).multiply(new BigInteger(o.pricePerToken())).divide(PRICE_SCALE);
        int feeBps = P2P_FEE_BPS[0]; // Pire cas (Bronze) pour ne jamais sous-estimer la réserve
        BigInteger feeBase = notional.multiply(BigInteger.valueOf(feeBps)).divide(BigInteger.valueOf(BPS));
        BigInteger vat = feeBase.multiply(BigInteger.valueOf(VAT_BPS)).divide(BigInteger.valueOf(BPS));
        reservedCash = reservedCash.add(notional).add(feeBase).add(vat);
      } else {
        String tk = o.token().toLowerCase();
        BigInteger amt = new BigInteger(o.tokenAmount());
        reservedTokens.merge(tk, amt, BigInteger::add);
      }
    }

    Map<String, String> reservedTokensStr = new HashMap<>();
    reservedTokens.forEach((k, v) -> reservedTokensStr.put(k, v.toString()));
    return new OrderBookDtos.P2PReservationsResponse(wallet, reservedCash.toString(), reservedTokensStr);
  }

  /**
   * Vérifie que l'utilisateur a un solde disponible suffisant (après réservations P2P) pour cet ordre.
   */
  private void validateAvailableBalance(String walletAddress, OrderSide side, String token,
      String tokenAmount, String pricePerToken, int feeLevel) {
    OrderBookDtos.P2PReservationsResponse res = getReservations(walletAddress);
    PortfolioResponse port = blockchainRead.portfolio(walletAddress);

    if (side == OrderSide.BUY) {
      BigInteger notional = new BigInteger(tokenAmount).multiply(new BigInteger(pricePerToken)).divide(PRICE_SCALE);
      int feeBps = P2P_FEE_BPS[Math.min(Math.max(0, feeLevel), 4)];
      BigInteger feeBase = notional.multiply(BigInteger.valueOf(feeBps)).divide(BigInteger.valueOf(BPS));
      BigInteger vat = feeBase.multiply(BigInteger.valueOf(VAT_BPS)).divide(BigInteger.valueOf(BPS));
      BigInteger thisOrderTotal = notional.add(feeBase).add(vat);

      BigInteger cashBal = new BigInteger(port.cashBalanceTnd());
      BigInteger reservedCash = new BigInteger(res.reservedCashTnd1e8());
      BigInteger available = cashBal.subtract(reservedCash);
      if (available.compareTo(thisOrderTotal) < 0) {
        throw new IllegalStateException(
            "Solde disponible insuffisant pour cet ordre P2P. Vous avez des ordres en attente qui réservent "
                + from1e8(reservedCash) + " TND. Disponible: " + from1e8(available) + " TND. Requis: " + from1e8(thisOrderTotal) + " TND.");
      }
    } else {
      BigInteger tokenAmt = new BigInteger(tokenAmount);
      String reservedStr = res.reservedTokens1e8().getOrDefault(token.toLowerCase(), "0");
      BigInteger reservedTokens = new BigInteger(reservedStr);

      Optional<PortfolioPosition> pos = port.positions().stream()
          .filter(p -> p.token().equalsIgnoreCase(token))
          .findFirst();
      if (pos.isEmpty()) {
        throw new IllegalStateException("Token non trouvé dans votre portefeuille.");
      }
      BigInteger balance = new BigInteger(pos.get().balanceTokens());
      BigInteger locked = new BigInteger(pos.get().lockedTokens1e8());
      BigInteger available = balance.subtract(locked).subtract(reservedTokens);
      if (available.compareTo(tokenAmt) < 0) {
        throw new IllegalStateException(
            "Tokens disponibles insuffisants pour cet ordre P2P. Vous avez des ordres de vente en attente qui réservent une partie de vos tokens. Disponible: "
                + from1e8(available) + ". Requis: " + from1e8(tokenAmt));
      }
    }
  }

  private static String from1e8(BigInteger v) {
    return v.divide(BigInteger.valueOf(100_000_000)).toString();
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
   * Retourne tous les ordres (pour backoffice P2P/Fallback).
   */
  public java.util.List<Order> getAllOrdersForBackoffice() {
    return new ArrayList<>(ordersById.values());
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
        order.settlementTxHash(),
        order.filledTokenAmount()
    );
    ordersById.put(orderId, cancelled);

    return new OrderBookDtos.CancelOrderResponse(orderId, true, "Order cancelled");
  }

  /**
   * Heuristique de probabilité de matching (ordres ouverts côté opposé).
   */
  public OrderBookDtos.MatchingProbabilityResponse getMatchingProbability(String token, int periodHours) {
    OrderSide buySide = OrderSide.BUY;
    OrderSide sellSide = OrderSide.SELL;
    int buyCount = countPendingOrders(token, buySide);
    int sellCount = countPendingOrders(token, sellSide);
    int total = buyCount + sellCount;
    String label = total >= 5 ? "Élevée" : total >= 2 ? "Moyenne" : "Faible";
    String msg = total >= 5
        ? "Plusieurs ordres ouverts sur le carnet. Probabilité de matching favorable."
        : total >= 2
            ? "Quelques ordres ouverts. Le fallback vers la piscine exécutera le reliquat à l'expiration."
            : "Peu d'ordres ouverts. En cas de non-matching, exécution automatique via la piscine.";
    return new OrderBookDtos.MatchingProbabilityResponse(
        token != null ? token : "",
        periodHours,
        label,
        msg,
        total
    );
  }

  private int countPendingOrders(String token, OrderSide side) {
    var resp = listOrders(token, side);
    return resp.orders() != null ? resp.orders().size() : 0;
  }

  /**
   * Traite les ordres PENDING expirés: exécute le fallback vers la piscine.
   * Appelé par OrderScheduler.
   */
  public void processExpiredOrders() {
    long now = java.time.Instant.now().getEpochSecond();
    java.util.List<Order> toProcess = new java.util.ArrayList<>();
    ordersById.values().forEach(o -> {
      if (o.status() == OrderStatus.PENDING && o.deadline() < now) {
        toProcess.add(o);
      }
    });
    for (Order order : toProcess) {
      try {
        removeOrderFromBook(order);
        String txHash = fallbackService.executeFallback(order);
        OrderStatus newStatus = (txHash != null && !txHash.isBlank()) ? OrderStatus.SETTLED : OrderStatus.EXPIRED;
        Order updated = new Order(
            order.orderId(), order.maker(), order.side(), order.token(), order.tokenAmount(),
            order.pricePerToken(), order.nonce(), order.deadline(), newStatus, order.createdAt(),
            order.signature(), order.matchedOrderId(), txHash, order.filledTokenAmount());
        ordersById.put(order.orderId(), updated);
        if (txHash != null && !txHash.isBlank()) {
          String fundName = registry.findByToken(order.token()).map(f -> f.name()).orElse("CPEF");
          fallbackService.notifyAndPushScore(order.maker(), fundName, order.side() == OrderSide.BUY);
        }
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(OrderBookService.class)
            .warn("Fallback failed for order {}: {}", order.orderId(), e.getMessage());
        Order expired = new Order(
            order.orderId(), order.maker(), order.side(), order.token(), order.tokenAmount(),
            order.pricePerToken(), order.nonce(), order.deadline(), OrderStatus.EXPIRED,
            order.createdAt(), order.signature(), order.matchedOrderId(), null, order.filledTokenAmount());
        ordersById.put(order.orderId(), expired);
      }
    }
  }

  // ========== Méthodes privées ==========

  private record MatchResult(Order oppositeOrder, BigInteger matchedAmount) {}

  private Optional<MatchResult> tryMatchOrder(Order newOrder) {
    String token = newOrder.token();
    OrderSide oppositeSide = newOrder.side() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    
    Map<OrderSide, List<Order>> tokenBook = orderBook.get(token);
    if (tokenBook == null) return Optional.empty();

    List<Order> oppositeOrders = tokenBook.get(oppositeSide);
    if (oppositeOrders == null || oppositeOrders.isEmpty()) return Optional.empty();

    BigInteger newPrice = new BigInteger(newOrder.pricePerToken());
    BigInteger newAmount = new BigInteger(newOrder.tokenAmount());

    // Chercher un ordre compatible (même prix ou meilleur, montants compatibles -> matching partiel)
    for (Order opposite : oppositeOrders) {
      if (opposite.status() != OrderStatus.PENDING) continue;
      if (opposite.maker().equalsIgnoreCase(newOrder.maker())) continue; // Pas de self-trade

      BigInteger oppositePrice = new BigInteger(opposite.pricePerToken());
      BigInteger oppositeAmount = new BigInteger(opposite.tokenAmount());

      // Vérifier la compatibilité des prix
      boolean priceMatch = false;
      if (newOrder.side() == OrderSide.BUY) {
        priceMatch = oppositePrice.compareTo(newPrice) <= 0;
      } else {
        priceMatch = oppositePrice.compareTo(newPrice) >= 0;
      }

      if (priceMatch) {
        // Matching partiel : on matche le minimum des deux montants
        BigInteger matchedAmount = newAmount.min(oppositeAmount);
        if (matchedAmount.signum() > 0) {
          return Optional.of(new MatchResult(opposite, matchedAmount));
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
