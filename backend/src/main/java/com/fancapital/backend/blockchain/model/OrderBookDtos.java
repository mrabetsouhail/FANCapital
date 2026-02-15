package com.fancapital.backend.blockchain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/**
 * DTOs pour le système d'order book P2P.
 */
public class OrderBookDtos {
  public enum OrderSide {
    BUY, SELL
  }

  public enum OrderStatus {
    PENDING,    // Ordre soumis, en attente de matching
    MATCHED,    // Ordre matché, settlement en cours
    SETTLED,    // Ordre réglé avec succès
    CANCELLED,  // Ordre annulé par l'utilisateur
    EXPIRED     // Ordre expiré (deadline dépassée)
  }

  /**
   * Requête pour soumettre un nouvel ordre P2P.
   * Note: Le champ 'maker' est ignoré - l'utilisateur connecté (JWT) est utilisé automatiquement.
   */
  public record SubmitOrderRequest(
      String maker, // Ignoré - remplacé par l'utilisateur connecté via JWT (WaaS)
      @NotBlank String side, // "buy" or "sell"
      @NotBlank @Pattern(regexp = "^0x[a-fA-F0-9]{40}$") String token,
      @NotBlank String tokenAmount, // 1e8
      @NotBlank String pricePerToken, // 1e8
      String nonce, // uint256 as string (optional, auto-generated if not provided)
      String deadline, // unix seconds as string (optional, defaults to +1 hour)
      String signature // Ignoré - pas nécessaire avec WaaS
  ) {}

  /**
   * Représentation d'un ordre dans le book.
   * filledTokenAmount: montant déjà matché (1e8), pour matching partiel.
   */
  public record Order(
      String orderId,           // UUID unique
      String maker,             // Adresse du créateur de l'ordre
      OrderSide side,           // BUY ou SELL
      String token,             // Adresse du token CPEF
      String tokenAmount,       // 1e8 - montant total
      String pricePerToken,     // 1e8
      String nonce,             // uint256 as string
      long deadline,            // unix timestamp - période de validité (TTL)
      OrderStatus status,       // État actuel de l'ordre
      Instant createdAt,        // Date de création
      String signature,         // Signature wallet (optionnelle)
      String matchedOrderId,   // ID de l'ordre matché (si applicable)
      String settlementTxHash,  // Hash de la transaction de settlement (si settled)
      String filledTokenAmount // 1e8 - montant déjà exécuté (P2P matché)
  ) {}

  /**
   * Réponse après soumission d'un ordre.
   * poolSpreadWarning: avertissement si le fallback vers la piscine applique un spread différent du P2P.
   */
  public record SubmitOrderResponse(
      String orderId,
      OrderStatus status,
      String message,
      Order matchedOrder, // Si l'ordre a été immédiatement matché
      String poolSpreadWarning // Ex: "En cas de non-matching, le reliquat sera exécuté via la piscine (spread ~0.2% différent du P2P)."
  ) {}

  /**
   * Liste des ordres ouverts.
   */
  public record OrdersListResponse(
      java.util.List<Order> orders,
      int totalCount
  ) {}

  /**
   * Réponse après annulation d'un ordre.
   */
  public record CancelOrderResponse(
      String orderId,
      boolean cancelled,
      String message
  ) {}

  /**
   * Probabilité de matching (heuristique) pour informer l'utilisateur.
   */
  public record MatchingProbabilityResponse(
      String token,
      int periodHours,
      String probabilityLabel, // Ex: "Élevée", "Moyenne", "Faible"
      String message,
      int openOrdersOpposite
  ) {}

  /**
   * Montants réservés par les ordres P2P en attente (PENDING ou MATCHED non réglés).
   * Ces montants ne doivent pas être utilisés pour d'autres ordres tant que l'ordre n'est pas exécuté ou annulé.
   */
  public record P2PReservationsResponse(
      String wallet,
      String reservedCashTnd1e8, // TND réservés (achats P2P en attente)
      java.util.Map<String, String> reservedTokens1e8  // token -> amount (1e8) réservés (ventes P2P en attente)
  ) {}
}
