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
 * Service pour gérer la Burn Key (clé de destruction de tokens).
 * 
 * Selon le Livre Blanc Technique v3.0, la Burn Key détruit les jetons
 * lors des rachats (burn de CashTokenTND).
 * 
 * En production, cette clé doit être stockée dans un HSM (Hardware Security Module).
 */
@Service
public class BurnKeyService {
  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;

  public BurnKeyService(Web3j web3j, BlockchainProperties props, DeploymentInfraService infra) {
    this.web3j = web3j;
    this.props = props;
    this.infra = infra;
  }

  /**
   * Burn (détruire) des CashTokenTND depuis une adresse.
   * 
   * @param from Adresse source
   * @param amount Montant en TND (scaled 1e8)
   * @return Hash de la transaction
   */
  public String burn(String from, BigInteger amount) {
    String burnKey = props.burnPrivateKey();
    if (burnKey == null || burnKey.isBlank()) {
      throw new IllegalStateException("BURN_PRIVATE_KEY not configured (blockchain.burn-private-key). This key should be stored in HSM for production.");
    }

    String cashToken = infra.cashTokenAddress();
    if (cashToken == null || cashToken.isBlank()) {
      throw new IllegalStateException("CashTokenTND address not configured in deployments infra.");
    }

    Credentials credentials = Credentials.create(burnKey.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function(
        "burn",
        List.of(new Address(from), new Uint256(amount)),
        List.of()
    );
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(250_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, cashToken, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("burn failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("burn RPC error: " + e.getMessage(), e);
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
