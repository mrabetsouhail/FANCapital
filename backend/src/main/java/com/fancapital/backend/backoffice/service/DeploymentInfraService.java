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

  public String p2pExchangeAddress() {
    return load().infra().get("P2PExchange");
  }

  public String circuitBreakerAddress() {
    return load().infra().get("CircuitBreaker");
  }

  /** MultiSigCouncil address (from deployments infra or council json). */
  public String multiSigCouncilAddress() {
    String fromInfra = load().infra().get("MultiSigCouncil");
    if (fromInfra != null && !fromInfra.isBlank()) return fromInfra.trim();
    try {
      Path councilPath = Path.of("..", "blockchain", "deployments", "localhost.council.json");
      if (Files.exists(councilPath)) {
        String raw = Files.readString(councilPath);
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(raw);
        if (root.has("council") && root.get("council").has("address")) {
          return root.get("council").get("address").asText().trim();
        }
      }
    } catch (IOException e) {
      // ignore
    }
    return null;
  }

  public String creditModelAAddress() {
    String from = load().infra().get("CreditModelA");
    if (from != null && !from.isBlank()) return from.trim();
    return fromLocalhostJson("CreditModelA");
  }

  public String escrowRegistryAddress() {
    String from = load().infra().get("EscrowRegistry");
    if (from != null && !from.isBlank()) return from.trim();
    return fromLocalhostJson("EscrowRegistry");
  }

  public String priceOracleAddress() {
    // PriceOracle is per-fund, but we can return the first one found
    // For shared oracle, it should be in infra
    String oracle = load().infra().get("PriceOracle");
    if (oracle != null && !oracle.isBlank()) {
      return oracle;
    }
    // Fallback: try to get from first fund in registry
    // This is a workaround - ideally PriceOracle should be in infra
    return null; // Will be handled by caller
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
      
      // Try to find localhost.json (from deploy.ts) or use DeploymentRegistry path
      String path = findDeploymentsFile();
      Map<String, String> contracts = new java.util.HashMap<>();
      
      try {
        String raw = Files.readString(Path.of(path));
        DeploymentsFile df = objectMapper.readValue(raw, DeploymentsFile.class);
        // Support both "infra" and "contracts" keys (deploy.ts uses "contracts")
        Map<String, String> primary = df.contracts() != null ? df.contracts() : df.infra();
        if (primary != null && !primary.isEmpty()) {
          contracts.putAll(primary);
        }
        
        // If P2PExchange is missing, try to load from localhost.json as fallback
        if (!contracts.containsKey("P2PExchange")) {
          Path localhostJson = Path.of("..", "blockchain", "deployments", "localhost.json");
          if (Files.exists(localhostJson) && !path.equals(localhostJson.toAbsolutePath().toString())) {
            try {
              String localhostRaw = Files.readString(localhostJson);
              DeploymentsFile localhostDf = objectMapper.readValue(localhostRaw, DeploymentsFile.class);
              Map<String, String> localhostContracts = localhostDf.contracts() != null ? localhostDf.contracts() : localhostDf.infra();
              if (localhostContracts != null && localhostContracts.containsKey("P2PExchange")) {
                contracts.put("P2PExchange", localhostContracts.get("P2PExchange"));
              }
            } catch (IOException e) {
              // Ignore fallback errors
            }
          }
        }
        
        if (contracts.isEmpty()) {
          throw new IllegalStateException("deployments json has no 'contracts' or 'infra' object: " + path);
        }
        c = new InfraCache(path, contracts);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read deployments json: " + path, e);
      }
      cache = c;
      return c;
    }
  }

  private String fromLocalhostJson(String contract) {
    try {
      Path p = Path.of("..", "blockchain", "deployments", "localhost.json");
      if (Files.exists(p)) {
        String raw = Files.readString(p);
        DeploymentsFile df = objectMapper.readValue(raw, DeploymentsFile.class);
        Map<String, String> c = df.contracts() != null ? df.contracts() : df.infra();
        if (c != null) {
          String v = c.get(contract);
          if (v != null && !v.isBlank()) return v.trim();
        }
      }
    } catch (IOException e) {
      // ignore
    }
    return null;
  }

  private String findDeploymentsFile() {
    // Try localhost.factory-funds.json first (contains the actual deployed contracts)
    Path factoryFundsJson = Path.of("..", "blockchain", "deployments", "localhost.factory-funds.json");
    if (Files.exists(factoryFundsJson)) {
      return factoryFundsJson.toAbsolutePath().toString();
    }
    // Fallback to localhost.json (from deploy.ts)
    Path localhostJson = Path.of("..", "blockchain", "deployments", "localhost.json");
    if (Files.exists(localhostJson)) {
      return localhostJson.toAbsolutePath().toString();
    }
    // Last fallback to DeploymentRegistry path (for factory/council deployments)
    return deploymentRegistry.getDeploymentsPathUsed();
  }

  private record InfraCache(String pathUsed, Map<String, String> infra) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DeploymentsFile(Map<String, String> infra, Map<String, String> contracts) {}
}

