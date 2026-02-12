package com.fancapital.backend.blockchain.model;

import jakarta.validation.constraints.NotBlank;

public class TxDtos {
  public record BuyRequest(@NotBlank String token, @NotBlank String user, @NotBlank String tndIn) {}

  public record SellRequest(@NotBlank String token, @NotBlank String user, @NotBlank String tokenAmount) {}

  public record AdvanceRequest(
      @NotBlank String user,
      @NotBlank String token,        // Atlas ou Didon
      long collateralAmount,        // nombre de tokens
      long durationDays
  ) {}

  public record P2PSettleRequest(
      @NotBlank String token,
      @NotBlank String seller,
      @NotBlank String buyer,
      @NotBlank String tokenAmount,
      @NotBlank String pricePerToken,
      // Optional fields to carry an off-chain signed order (MVP)
      String maker,
      String side,
      String nonce,
      String deadline,
      String signature
  ) {}

  public record TxResponse(String status, String txHash, String message) {}
}

