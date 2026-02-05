package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Service pour gérer la Onboarding Key (clé de validation KYC).
 * 
 * Selon le Dossier de Sécurité Institutionnelle v2.0, la Onboarding Key est utilisée
 * exclusivement pour la validation KYC et la création de comptes.
 * 
 * Cette séparation garantit qu'une vulnérabilité sur le service Onboarding ne peut
 * en aucun cas compromettre les fonctions de Minting, Oracle ou les autres services.
 * 
 * En production, cette clé doit être stockée dans un HSM (Hardware Security Module).
 */
@Service
public class OnboardingKeyService {
  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;

  public OnboardingKeyService(Web3j web3j, BlockchainProperties props, DeploymentInfraService infra) {
    this.web3j = web3j;
    this.props = props;
    this.infra = infra;
  }

  /**
   * Ajoute un utilisateur à la whitelist KYC.
   * 
   * @param userAddress Adresse de l'utilisateur
   * @param level Niveau KYC (1 = Green, 2 = White)
   * @param resident Statut de résidence (true = résident, false = non-résident)
   * @return Hash de la transaction
   */
  public String addToWhitelist(String userAddress, int level, boolean resident) {
    String onboardingKey = props.onboardingPrivateKey();
    if (onboardingKey == null || onboardingKey.isBlank()) {
      throw new IllegalStateException("ONBOARDING_PRIVATE_KEY not configured (blockchain.onboarding-private-key). This key should be stored in HSM for production.");
    }

    if (level != 1 && level != 2) {
      throw new IllegalArgumentException("KYC level must be 1 (Green) or 2 (White)");
    }

    String kycRegistry = infra.kycRegistryAddress();
    if (kycRegistry == null || kycRegistry.isBlank()) {
      throw new IllegalStateException("KYCRegistry address not configured in deployments infra.");
    }

    Credentials credentials = Credentials.create(onboardingKey.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function(
        "addToWhitelist",
        List.of(new Address(userAddress), new Uint8(BigInteger.valueOf(level)), new Bool(resident)),
        List.of()
    );
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(250_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, kycRegistry, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("addToWhitelist failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("addToWhitelist RPC error: " + e.getMessage(), e);
    }
  }

  /**
   * Retire un utilisateur de la whitelist KYC.
   * 
   * @param userAddress Adresse de l'utilisateur
   * @return Hash de la transaction
   */
  public String removeFromWhitelist(String userAddress) {
    String onboardingKey = props.onboardingPrivateKey();
    if (onboardingKey == null || onboardingKey.isBlank()) {
      throw new IllegalStateException("ONBOARDING_PRIVATE_KEY not configured (blockchain.onboarding-private-key).");
    }

    String kycRegistry = infra.kycRegistryAddress();
    if (kycRegistry == null || kycRegistry.isBlank()) {
      throw new IllegalStateException("KYCRegistry address not configured in deployments infra.");
    }

    Credentials credentials = Credentials.create(onboardingKey.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function(
        "removeFromWhitelist",
        List.of(new Address(userAddress)),
        List.of()
    );
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(250_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, kycRegistry, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("removeFromWhitelist failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("removeFromWhitelist RPC error: " + e.getMessage(), e);
    }
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
