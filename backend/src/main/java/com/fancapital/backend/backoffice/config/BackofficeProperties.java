package com.fancapital.backend.backoffice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backoffice")
public record BackofficeProperties(
    Tax tax
) {
  public record Tax(
      String taxVaultAddress,
      String cashTokenAddress,
      String governancePrivateKey,
      List<String> adminEmails
  ) {}
}

