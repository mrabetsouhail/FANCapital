package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.config.BlockchainProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DeploymentRegistry {
  private final ObjectMapper objectMapper;
  private final BlockchainProperties props;

  private volatile Deployments deploymentsCache;

  public DeploymentRegistry(ObjectMapper objectMapper, BlockchainProperties props) {
    this.objectMapper = objectMapper;
    this.props = props;
  }

  public List<FundDto> listFunds() {
    return load().funds().stream()
        .sorted(Comparator.comparingInt(FundDto::id))
        .toList();
  }

  public Optional<FundDto> getFund(int id) {
    return load().funds().stream().filter(f -> f.id() == id).findFirst();
  }

  public Optional<FundDto> findByToken(String tokenAddress) {
    final String t = tokenAddress.toLowerCase();
    return load().funds().stream().filter(f -> f.token().equalsIgnoreCase(t)).findFirst();
  }

  /** Résout Atlas/Didon (UI) vers l'adresse du token. */
  public String getTokenAddressForSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) return null;
    String s = symbol.toLowerCase().strip();
    for (FundDto f : load().funds()) {
      String name = (f.name() != null ? f.name() : "").toLowerCase();
      String sym = (f.symbol() != null ? f.symbol() : "").toLowerCase();
      if ((name.contains("atlas") || sym.contains("atlas")) && s.contains("atlas")) return f.token();
      if ((name.contains("didon") || sym.contains("didon")) && s.contains("didon")) return f.token();
    }
    return null;
  }

  public String getDeploymentsPathUsed() {
    return load().pathUsed();
  }

  /** Adresse CreditModelA depuis localhost.json (fallback pour AST). */
  public String getCreditModelAAddress() {
    return getContractFromLocalhost("CreditModelA");
  }

  /** Adresse EscrowRegistry depuis localhost.json. */
  public String getEscrowRegistryAddress() {
    return getContractFromLocalhost("EscrowRegistry");
  }

  private String getContractFromLocalhost(String name) {
    try {
      Path p = Path.of("..", "blockchain", "deployments", "localhost.json");
      if (!Files.exists(p)) return null;
      String raw = Files.readString(p);
      var tree = objectMapper.readTree(raw);
      var contracts = tree.has("contracts") ? tree.get("contracts") : tree.get("infra");
      if (contracts != null && contracts.has(name)) {
        String v = contracts.get(name).asText();
        return (v != null && !v.isBlank()) ? v.trim() : null;
      }
    } catch (IOException e) {
      // ignore
    }
    return null;
  }

  private Deployments load() {
    Deployments cached = deploymentsCache;
    if (cached != null) return cached;
    synchronized (this) {
      cached = deploymentsCache;
      if (cached != null) return cached;

      String configured = props.deploymentsPath();
      List<Path> candidates = new ArrayList<>();
      if (configured != null && !configured.isBlank()) {
        candidates.add(Path.of(configured));
      }
      candidates.add(Path.of("..", "blockchain", "deployments", "localhost.council-funds.json"));
      candidates.add(Path.of("..", "blockchain", "deployments", "localhost.factory-funds.json"));

      Path found = candidates.stream().filter(Files::exists).findFirst()
          .orElseThrow(() -> new IllegalStateException("No deployments json found. Set blockchain.deployments-path."));

      try {
        String raw = Files.readString(found);
        DeploymentsFile df = objectMapper.readValue(raw, DeploymentsFile.class);
        List<FundDto> funds = df.funds().stream()
            .map(f -> new FundDto(
                f.id(),
                f.name() != null ? f.name() : "CPEF Fund " + f.id(),
                f.symbol() != null ? f.symbol() : "",
                f.token(),
                f.pool(),
                f.oracle(),
                f.createdAt() != null ? f.createdAt() : df.generatedAt()
            ))
            .toList();
        cached = new Deployments(found.toAbsolutePath().toString(), funds);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read deployments json: " + found, e);
      }

      deploymentsCache = cached;
      return cached;
    }
  }

  private record Deployments(String pathUsed, List<FundDto> funds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DeploymentsFile(List<FundEntry> funds, String generatedAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FundEntry(int id, String name, String symbol, String token, String pool, String oracle, String createdAt) {}
}

