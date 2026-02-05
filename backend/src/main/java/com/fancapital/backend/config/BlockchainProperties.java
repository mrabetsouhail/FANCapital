package com.fancapital.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blockchain")
public record BlockchainProperties(
    String rpcUrl,
    String deploymentsPath,
    PriceOverrides priceOverrides,
    // Private key for signing LiquidityPool buyFor/sellFor transactions (OPERATOR_ROLE).
    // MUST be provided via environment variable.
    String operatorPrivateKey,
    // Panic Key (cold storage) for emergency global pause (PANIC_KEY_ROLE on CircuitBreaker).
    // This key should be stored offline and only used in emergency situations.
    String panicPrivateKey,
    // Mint Key (HSM) for minting CashTokenTND (MINTER_ROLE on CashTokenTND).
    // In production, this key should be stored in a Hardware Security Module (HSM).
    String mintPrivateKey,
    // Burn Key (HSM) for burning CashTokenTND (BURNER_ROLE on CashTokenTND).
    // In production, this key should be stored in a Hardware Security Module (HSM).
    String burnPrivateKey,
    // Oracle Key (HSM) for updating VNI prices from BVMT (ORACLE_ROLE on PriceOracle).
    // According to Dossier de Sécurité v2.0, this key is dedicated exclusively to price updates.
    // In production, this key should be stored in a Hardware Security Module (HSM).
    String oraclePrivateKey,
    // Onboarding Key (HSM) for KYC validation and account creation (KYC_VALIDATOR_ROLE on KYCRegistry).
    // According to Dossier de Sécurité v2.0, this key is used exclusively for KYC validation.
    // In production, this key should be stored in a Hardware Security Module (HSM).
    String onboardingPrivateKey
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

