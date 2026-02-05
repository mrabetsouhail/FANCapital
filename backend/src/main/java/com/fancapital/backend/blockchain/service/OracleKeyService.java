package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.service.DeploymentInfraService;
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
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Service pour gérer la Oracle Key (clé de mise à jour des prix).
 * 
 * Selon le Dossier de Sécurité Institutionnelle v2.0, la Oracle Key est dédiée
 * exclusivement à la mise à jour des prix en provenance de la BVMT.
 * 
 * Cette séparation garantit qu'une vulnérabilité sur le service Oracle ne peut
 * en aucun cas compromettre les fonctions de Minting ou les avoirs des utilisateurs.
 * 
 * En production, cette clé doit être stockée dans un HSM (Hardware Security Module).
 */
@Service
public class OracleKeyService {
  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;

  public OracleKeyService(Web3j web3j, BlockchainProperties props, DeploymentInfraService infra) {
    this.web3j = web3j;
    this.props = props;
    this.infra = infra;
  }

  /**
   * Met à jour le VNI (Valeur Nette d'Inventaire) pour un token.
   * 
   * @param tokenAddress Adresse du token CPEF
   * @param vniTnd VNI en TND (scaled 1e8)
   * @return Hash de la transaction
   */
  public String updateVNI(String tokenAddress, BigInteger vniTnd) {
    String oracleKey = props.oraclePrivateKey();
    if (oracleKey == null || oracleKey.isBlank()) {
      throw new IllegalStateException("ORACLE_PRIVATE_KEY not configured (blockchain.oracle-private-key). This key should be stored in HSM for production.");
    }

    String priceOracle = infra.priceOracleAddress();
    if (priceOracle == null || priceOracle.isBlank()) {
      throw new IllegalStateException("PriceOracle address not configured in deployments infra.");
    }

    Credentials credentials = Credentials.create(oracleKey.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function(
        "updateVNI",
        List.of(new Address(tokenAddress), new Uint256(vniTnd)),
        List.of()
    );
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(200_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, priceOracle, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("updateVNI failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("updateVNI RPC error: " + e.getMessage(), e);
    }
  }

  /**
   * Force la mise à jour du VNI (nécessite GOVERNANCE_ROLE).
   * 
   * @param tokenAddress Adresse du token CPEF
   * @param vniTnd VNI en TND (scaled 1e8)
   * @return Hash de la transaction
   */
  public String forceUpdateVNI(String tokenAddress, BigInteger vniTnd) {
    // Force update nécessite GOVERNANCE_ROLE, pas ORACLE_ROLE
    // Cette méthode devrait utiliser GOV_PRIVATE_KEY ou Multi-Sig
    throw new UnsupportedOperationException("forceUpdateVNI requires GOVERNANCE_ROLE. Use Multi-Sig or Governance service.");
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
