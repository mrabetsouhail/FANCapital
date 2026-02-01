package com.fancapital.backend.backoffice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backoffice")
public record BackofficeProperties(
    Tax tax,
    Audit audit
) {
  public record Tax(
      String taxVaultAddress,
      String cashTokenAddress,
      String governancePrivateKey,
      List<String> adminEmails
  ) {}

  /**
   * Immutable audit register roles (read-only regulator, export-capable compliance).
   *
   * These roles are enforced server-side by email allow-lists.
   */
  public record Audit(
      List<String> regulatorEmails,
      List<String> complianceEmails,
      boolean reconciliationEnabled,
      long reconciliationIntervalMs
  ) {}
}

