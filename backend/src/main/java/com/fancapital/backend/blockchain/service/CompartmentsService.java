package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.TypeReference;

/**
 * Lit les adresses des 4 compartiments (Matrice) depuis le fichier deployments.
 * Architecture : A=Réserve Liquidité, B=Sas Partenaires, C=Revenus, D=Fonds Garantie.
 * Récupère les soldes TND on-chain pour chaque piscine.
 */
@Service
public class CompartmentsService {
  private final ObjectMapper objectMapper;
  private final DeploymentRegistry deploymentRegistry;
  private final EvmCallService evm;
  private final DeploymentInfraService infra;

  public CompartmentsService(
      ObjectMapper objectMapper,
      DeploymentRegistry deploymentRegistry,
      EvmCallService evm,
      DeploymentInfraService infra) {
    this.objectMapper = objectMapper;
    this.deploymentRegistry = deploymentRegistry;
    this.evm = evm;
    this.infra = infra;
  }

  /**
   * Retourne les infos des 4 compartiments si présentes dans le déploiement.
   */
  public Optional<MatriceInfo> getMatrice() {
    try {
      String pathUsed = deploymentRegistry.getDeploymentsPathUsed();
      if (pathUsed == null || pathUsed.isBlank()) return Optional.empty();

      Path p = Path.of(pathUsed);
      if (!Files.exists(p)) return Optional.empty();

      JsonNode root = objectMapper.readTree(Files.readString(p));
      JsonNode matrice = root.get("matrice");
      if (matrice == null || !matrice.isObject()) return Optional.empty();

      String reg = text(matrice, "CompartmentsRegistry");
      String a = text(matrice, "piscineA");
      String b = text(matrice, "piscineB");
      String c = text(matrice, "piscineC");
      String d = text(matrice, "piscineD");
      int bps = matrice.has("guaranteeFundBps") ? matrice.get("guaranteeFundBps").asInt(0) : 0;

      if (reg == null && a == null && b == null && c == null && d == null) return Optional.empty();

      String cashToken = infra.cashTokenAddress();
      // Piscine A = somme de TOUTES les pools (Atlas + Didon + ...) = Réserve de Liquidité complète
      BigInteger balASum = BigInteger.ZERO;
      if (cashToken != null) {
        for (var fund : deploymentRegistry.listFunds()) {
          String poolAddr = fund.pool();
          if (poolAddr != null && !poolAddr.isBlank()) {
            balASum = balASum.add(balanceOfTndBigInt(cashToken, poolAddr));
          }
        }
      }
      String balA = balASum.toString();
      String balB = (b != null && cashToken != null) ? balanceOfTnd(cashToken, b) : null;
      String balC = (c != null && cashToken != null) ? balanceOfTnd(cashToken, c) : null;
      String balD = (d != null && cashToken != null) ? balanceOfTnd(cashToken, d) : null;

      return Optional.of(new MatriceInfo(
          reg,
          a,
          b,
          c,
          d,
          bps,
          balA,
          balB,
          balC,
          balD
      ));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static String text(JsonNode node, String key) {
    if (!node.has(key)) return null;
    String v = node.get(key).asText(null);
    return (v != null && !v.isBlank()) ? v.trim() : null;
  }

  @SuppressWarnings("rawtypes")
  private String balanceOfTnd(String cashToken, String holder) {
    return balanceOfTndBigInt(cashToken, holder).toString();
  }

  @SuppressWarnings("rawtypes")
  private BigInteger balanceOfTndBigInt(String cashToken, String holder) {
    try {
      Function fn = new Function(
          "balanceOf",
          java.util.List.of(new Address(holder)),
          java.util.List.of(TypeReference.create(Uint256.class))
      );
      Type t = evm.ethCall(cashToken, fn).get(0);
      return (BigInteger) t.getValue();
    } catch (Exception e) {
      return BigInteger.ZERO;
    }
  }

  public record MatriceInfo(
      String compartmentsRegistry,
      String piscineA,
      String piscineB,
      String piscineC,
      String piscineD,
      int guaranteeFundBps,
      String balancePiscineATnd,
      String balancePiscineBTnd,
      String balancePiscineCTnd,
      String balancePiscineDTnd
  ) {}
}
