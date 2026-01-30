package com.fancapital.backend.blockchain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class QuoteDtos {
  private static final String ETH_ADDRESS_RX = "^0x[a-fA-F0-9]{40}$";
  private static final String UINT_STR_RX = "^[0-9]{1,78}$";

  public record QuoteBuyRequest(
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String token,
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String user,
      @NotBlank @Pattern(regexp = UINT_STR_RX) @Size(max = 78) String tndIn
  ) {}

  public record QuoteSellRequest(
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String token,
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String user,
      @NotBlank @Pattern(regexp = UINT_STR_RX) @Size(max = 78) String tokenAmount
  ) {}

  public record QuoteBuyResponse(
      String priceClient,
      String minted,
      String feeBase,
      String vat,
      String totalFee
  ) {}

  public record QuoteSellResponse(
      String priceClient,
      String tndOut,
      String feeBase,
      String vat,
      String totalFee,
      String tax
  ) {}
}

