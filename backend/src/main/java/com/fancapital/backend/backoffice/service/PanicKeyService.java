package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Service pour gérer la Panic Key (Bouton Panique) selon le Livre Blanc Technique v3.0.
 * 
 * La Panic Key est une clé privée stockée en cold storage qui permet de pause
 * instantanément toutes les opérations sur la blockchain en cas d'urgence.
 * 
 * Cette clé doit être configurée séparément de la clé de gouvernance normale
 * pour garantir une sécurité maximale.
 */
@Service
public class PanicKeyService {
  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;

  public PanicKeyService(Web3j web3j, BlockchainProperties props, DeploymentInfraService infra) {
    this.web3j = web3j;
    this.props = props;
    this.infra = infra;
  }

  /**
   * Active le Bouton Panique (pause globale de tous les contrats).
   * 
   * @param reason Raison de la pause (pour audit)
   * @return Hash de la transaction
   */
  public String pauseAll(String reason) {
    String panicKey = props.panicPrivateKey();
    if (panicKey == null || panicKey.isBlank()) {
      throw new IllegalStateException("PANIC_PRIVATE_KEY not configured (blockchain.panic-private-key). This key should be stored in cold storage.");
    }

    String circuitBreaker = infra.circuitBreakerAddress();
    if (circuitBreaker == null || circuitBreaker.isBlank()) {
      throw new IllegalStateException("CircuitBreaker address not configured in deployments infra.");
    }

    Credentials credentials = Credentials.create(panicKey.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function(
        "pauseAll",
        List.of(new Utf8String(reason != null ? reason : "Emergency pause triggered")),
        List.of()
    );
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(200_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, circuitBreaker, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("pauseAll failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("pauseAll RPC error: " + e.getMessage(), e);
    }
  }

  /**
   * Reprend les opérations (unpause global).
   * 
   * Note: La reprise nécessite GOVERNANCE_ROLE, pas PANIC_KEY_ROLE, pour sécurité.
   * 
   * @return Hash de la transaction
   */
  public String resumeAll() {
    // Resume requires GOVERNANCE_ROLE, not PANIC_KEY_ROLE
    // This should be called via MultiSig or Governance service
    throw new UnsupportedOperationException("Resume requires GOVERNANCE_ROLE. Use MultiSig or Governance service to resume operations.");
  }

  private BigInteger suggestedGasPrice() {
    try {
      EthGasPrice gp = web3j.ethGasPrice().send();
      if (gp.hasError()) {
        throw new IllegalStateException("eth_gasPrice error: " + gp.getError().getMessage());
      }
      BigInteger v = gp.getGasPrice();
      return v.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (IOException e) {
      return BigInteger.valueOf(1_000_000_000L);
    }
  }

  private BigInteger chainId() {
    try {
      return web3j.ethChainId().send().getChainId();
    } catch (IOException e) {
      return BigInteger.valueOf(31337);
    }
  }
}
