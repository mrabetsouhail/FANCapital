package com.fancapital.backend.blockchain.model;

import jakarta.validation.constraints.NotBlank;

public class TxDtos {
  public record BuyRequest(@NotBlank String token, @NotBlank String user, @NotBlank String tndIn) {}

  public record SellRequest(@NotBlank String token, @NotBlank String user, @NotBlank String tokenAmount) {}

  public record P2PSettleRequest(
      @NotBlank String token,
      @NotBlank String seller,
      @NotBlank String buyer,
      @NotBlank String tokenAmount,
      @NotBlank String pricePerToken
  ) {}

  public record TxResponse(String status, String txHash, String message) {}
}

