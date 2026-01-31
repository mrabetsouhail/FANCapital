package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeploymentInfraService {
  private final ObjectMapper objectMapper;
  private final DeploymentRegistry deploymentRegistry;
  private final BackofficeProperties props;

  private volatile InfraCache cache;

  public DeploymentInfraService(ObjectMapper objectMapper, DeploymentRegistry deploymentRegistry, BackofficeProperties props) {
    this.objectMapper = objectMapper;
    this.deploymentRegistry = deploymentRegistry;
    this.props = props;
  }

  public String taxVaultAddress() {
    String override = props.tax() != null ? props.tax().taxVaultAddress() : null;
    if (override != null && !override.isBlank()) return override.trim();
    return load().infra().get("TaxVault");
  }

  public String cashTokenAddress() {
    String override = props.tax() != null ? props.tax().cashTokenAddress() : null;
    if (override != null && !override.isBlank()) return override.trim();
    return load().infra().get("CashTokenTND");
  }

  public String kycRegistryAddress() {
    return load().infra().get("KYCRegistry");
  }

  public String investorRegistryAddress() {
    return load().infra().get("InvestorRegistry");
  }

  public String deploymentsPathUsed() {
    return load().pathUsed();
  }

  private InfraCache load() {
    InfraCache c = cache;
    if (c != null) return c;
    synchronized (this) {
      c = cache;
      if (c != null) return c;
      String path = deploymentRegistry.getDeploymentsPathUsed();
      try {
        String raw = Files.readString(Path.of(path));
        DeploymentsFile df = objectMapper.readValue(raw, DeploymentsFile.class);
        if (df.infra() == null || df.infra().isEmpty()) {
          throw new IllegalStateException("deployments json has no 'infra' object: " + path);
        }
        c = new InfraCache(path, df.infra());
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read deployments json: " + path, e);
      }
      cache = c;
      return c;
    }
  }

  private record InfraCache(String pathUsed, Map<String, String> infra) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DeploymentsFile(Map<String, String> infra) {}
}

