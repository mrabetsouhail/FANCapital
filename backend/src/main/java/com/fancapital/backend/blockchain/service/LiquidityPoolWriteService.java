package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.audit.service.BusinessContextService;
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

@Service
public class LiquidityPoolWriteService {
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L); // 1e8

  private final Web3j web3j;
  private final DeploymentRegistry registry;
  private final BlockchainProperties props;
  private final BusinessContextService businessContextService;

  public LiquidityPoolWriteService(
      Web3j web3j,
      DeploymentRegistry registry,
      BlockchainProperties props,
      BusinessContextService businessContextService
  ) {
    this.web3j = web3j;
    this.registry = registry;
    this.props = props;
    this.businessContextService = businessContextService;
  }

  public String buyFor(BuyRequest req) {
    var fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown fund token: " + req.token()));
    String pool = fund.pool();

    BigInteger tndIn = parseUint(req.tndIn(), "tndIn");

    // Dev: ensure on-chain oracle has an initialized VNI (required by pool staleness guard).
    ensureOraclePriceInitialized(fund.oracle(), req.token(), fund.name(), fund.id());

    Function fn = new Function(
        "buyFor",
        List.of(new Address(req.token()), new Address(req.user()), new Uint256(tndIn)),
        List.of()
    );

    String txHash = send(pool, fn, BigInteger.valueOf(1_000_000));
    
    // Enregistrer le BusinessContextId pour la traçabilité (Livre Blanc v2.1 - Section 4.2)
    String businessContextId = businessContextService.generateBusinessContextId("BUY");
    try {
      businessContextService.registerTransaction(
          txHash,
          businessContextId,
          pool,
          "BUY",
          String.format("Achat de tokens %s pour utilisateur %s, montant TND: %s", req.token(), req.user(), tndIn),
          null // accountingDocumentId sera ajouté par le backoffice si nécessaire
      );
    } catch (Exception e) {
      // Log l'erreur mais ne fait pas échouer la transaction
      System.err.println("Failed to register business context for transaction " + txHash + ": " + e.getMessage());
    }
    
    return txHash;
  }

  public String sellFor(SellRequest req) {
    var fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown fund token: " + req.token()));
    String pool = fund.pool();

    BigInteger tokenAmount = parseUint(req.tokenAmount(), "tokenAmount");

    ensureOraclePriceInitialized(fund.oracle(), req.token(), fund.name(), fund.id());

    Function fn = new Function(
        "sellFor",
        List.of(new Address(req.token()), new Address(req.user()), new Uint256(tokenAmount)),
        List.of()
    );

    String txHash = send(pool, fn, BigInteger.valueOf(1_200_000));
    
    // Enregistrer le BusinessContextId pour la traçabilité (Livre Blanc v2.1 - Section 4.2)
    String businessContextId = businessContextService.generateBusinessContextId("SELL");
    try {
      businessContextService.registerTransaction(
          txHash,
          businessContextId,
          pool,
          "SELL",
          String.format("Vente de tokens %s pour utilisateur %s, montant tokens: %s", req.token(), req.user(), tokenAmount),
          null // accountingDocumentId sera ajouté par le backoffice si nécessaire
      );
    } catch (Exception e) {
      // Log l'erreur mais ne fait pas échouer la transaction
      System.err.println("Failed to register business context for transaction " + txHash + ": " + e.getMessage());
    }
    
    return txHash;
  }

  private String send(String to, Function fn, BigInteger gasLimit) {
    String pk = props.operatorPrivateKey();
    if (pk == null || pk.isBlank()) {
      throw new IllegalStateException("OPERATOR_PRIVATE_KEY not configured (blockchain.operator-private-key).");
    }

    Credentials credentials = Credentials.create(pk.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    String data = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, to, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("EVM tx failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("EVM tx RPC error: " + e.getMessage(), e);
    }
  }

  private BigInteger suggestedGasPrice() {
    try {
      EthGasPrice gp = web3j.ethGasPrice().send();
      if (gp.hasError()) {
        throw new IllegalStateException("eth_gasPrice error: " + gp.getError().getMessage());
      }
      BigInteger v = gp.getGasPrice();
      // add a small buffer (x1.2) to avoid "too low for next block"
      return v.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (IOException e) {
      // fallback for local dev
      return BigInteger.valueOf(1_000_000_000L); // 1 gwei
    }
  }

  private BigInteger chainId() {
    try {
      return web3j.ethChainId().send().getChainId();
    } catch (IOException e) {
      return BigInteger.valueOf(31337);
    }
  }

  private void ensureOraclePriceInitialized(String oracle, String token, String fundName, int fundId) {
    BlockchainProperties.PriceOverrides po = props.priceOverrides();
    if (po == null || !po.enabled()) return;

    BigInteger vni1e8 = fixedVni1e8ForFund(po, fundName, fundId);
    if (vni1e8 == null) return;

    // PriceOracle.updateVNI(address token, uint256 newVni)
    // Note: This should use OracleKeyService for strict separation, but for now we keep using operator
    // TODO: Migrate to OracleKeyService for strict separation according to Dossier de Sécurité v2.0
    Function fn = new Function(
        "updateVNI",
        List.of(new Address(token), new Uint256(vni1e8)),
        List.of()
    );
    // best-effort: if this fails due to roles, caller will see later pool revert.
    send(oracle, fn, BigInteger.valueOf(250_000));
  }

  private BigInteger fixedVni1e8ForFund(BlockchainProperties.PriceOverrides po, String name, int id) {
    String n = name != null ? name.toLowerCase() : "";
    if (n.contains("atlas") || id == 0) return BigInteger.valueOf(po.atlasTnd()).multiply(PRICE_SCALE);
    if (n.contains("didon") || id == 1) return BigInteger.valueOf(po.didonTnd()).multiply(PRICE_SCALE);
    return null;
  }

  private static BigInteger parseUint(String raw, String field) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException(field + " is required");
    try {
      BigInteger v = new BigInteger(raw.trim());
      if (v.signum() < 0) throw new IllegalArgumentException(field + " must be >= 0");
      return v;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(field + " must be an integer string");
    }
  }
}

