package com.fancapital.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blockchain")
public record BlockchainProperties(
    String rpcUrl,
    String deploymentsPath,
    PriceOverrides priceOverrides,
    // Private key for signing LiquidityPool buyFor/sellFor transactions (OPERATOR_ROLE).
    // MUST be provided via environment variable.
    String operatorPrivateKey
) {
  public record PriceOverrides(
      boolean enabled,
      long atlasTnd,
      long didonTnd,
      int spreadBps,
      int feeBps,
      int vatBps
  ) {}
}

