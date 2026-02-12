package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.config.BlockchainProperties;
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

/**
 * Write service for CreditModelA. Calls activateAdvance, recordRepayment.
 */
@Service
public class CreditWriteService {

  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentRegistry registry;

  public CreditWriteService(Web3j web3j, BlockchainProperties props, DeploymentRegistry registry) {
    this.web3j = web3j;
    this.props = props;
    this.registry = registry;
  }

  /**
   * Activate an advance: lock collateral on-chain. Call AFTER crediting user's wallet.
   *
   * @param loanId Loan ID (status must be Requested)
   * @return transaction hash
   */
  public String activateAdvance(BigInteger loanId) throws IOException {
    String addr = registry.getCreditModelAAddress();
    if (addr == null || addr.isBlank()) {
      throw new IllegalStateException("CreditModelA address not configured.");
    }
    Function fn = new Function("activateAdvance", List.of(new Uint256(loanId)), List.of());
    return send(addr, fn, BigInteger.valueOf(400_000));
  }

  /**
   * Record a partial repayment and trigger prorata collateral release on-chain.
   *
   * @param loanId      Loan ID
   * @param amountTnd   Amount repaid in TND (scaled 1e8)
   * @return transaction hash
   */
  public String recordRepayment(BigInteger loanId, BigInteger amountTnd) throws IOException {
    String addr = registry.getCreditModelAAddress();
    if (addr == null || addr.isBlank()) {
      throw new IllegalStateException("CreditModelA address not configured.");
    }
    Function fn = new Function("recordRepayment",
        List.of(new Uint256(loanId), new Uint256(amountTnd)),
        List.of());
    return send(addr, fn, BigInteger.valueOf(300_000));
  }

  private String send(String to, Function fn, BigInteger gasLimit) throws IOException {
    String pk = props.operatorPrivateKey();
    if (pk == null || pk.isBlank()) {
      throw new IllegalStateException("OPERATOR_PRIVATE_KEY not configured.");
    }
    Credentials credentials = Credentials.create(pk.trim());
    BigInteger chainId;
    try {
      chainId = web3j.ethChainId().send().getChainId();
    } catch (IOException e) {
      chainId = BigInteger.valueOf(31337);
    }
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId.longValue());

    String data = FunctionEncoder.encode(fn);
    BigInteger gasPrice;
    try {
      gasPrice = web3j.ethGasPrice().send().getGasPrice();
    } catch (IOException e) {
      gasPrice = BigInteger.valueOf(20_000_000_000L);
    }
    if (gasPrice == null) {
      gasPrice = BigInteger.valueOf(20_000_000_000L);
    }
    gasPrice = gasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);

    EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, to, data, BigInteger.ZERO);
    if (tx.hasError()) {
      throw new IllegalStateException("EVM tx failed: " + tx.getError().getMessage());
    }
    return tx.getTransactionHash();
  }
}
