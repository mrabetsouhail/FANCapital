package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

@Service
public class TaxVaultWriteService {
  private final Web3j web3j;
  private final DeploymentInfraService infra;
  private final BackofficeProperties props;

  public TaxVaultWriteService(Web3j web3j, DeploymentInfraService infra, BackofficeProperties props) {
    this.web3j = web3j;
    this.infra = infra;
    this.props = props;
  }

  public String withdrawToFisc(BigInteger amount) {
    String pk = props.tax() != null ? props.tax().governancePrivateKey() : null;
    if (pk == null || pk.isBlank()) {
      throw new IllegalStateException("GOV_PRIVATE_KEY not configured (backoffice.tax.governance-private-key).");
    }
    String taxVault = infra.taxVaultAddress();
    if (taxVault == null || taxVault.isBlank()) throw new IllegalStateException("TaxVault address not configured");

    Credentials credentials = Credentials.create(pk.trim());
    BigInteger chainId = chainId();
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    Function fn = new Function("withdrawToFisc", List.of(new Uint256(amount)), List.of());
    String data = FunctionEncoder.encode(fn);

    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(500_000);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, taxVault, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("withdrawToFisc failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("withdrawToFisc RPC error: " + e.getMessage(), e);
    }
  }

  private BigInteger chainId() {
    try {
      return web3j.ethChainId().send().getChainId();
    } catch (IOException e) {
      // default hardhat
      return BigInteger.valueOf(31337);
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
      return BigInteger.valueOf(1_000_000_000L); // 1 gwei
    }
  }
}

