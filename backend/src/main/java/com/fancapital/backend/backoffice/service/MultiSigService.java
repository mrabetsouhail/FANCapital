package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.blockchain.service.EvmCallService;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Service pour interagir avec le contrat MultiSigCouncil (gouvernance N-of-M).
 * Submit, confirm, execute des transactions et lecture des propositions.
 */
@Service
@SuppressWarnings("rawtypes")
public class MultiSigService {
  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;
  private final EvmCallService evm;

  @SuppressWarnings("unused")
  public MultiSigService(Web3j web3j, BlockchainProperties props, DeploymentInfraService infra, EvmCallService evm) {
    this.web3j = web3j;
    this.props = props;
    this.infra = infra;
    this.evm = evm;
  }

  public String councilAddress() {
    String addr = infra.multiSigCouncilAddress();
    if (addr == null || addr.isBlank()) {
      throw new IllegalStateException("MultiSigCouncil address not configured. Add MultiSigCouncil to deployments infra or use localhost.council.json.");
    }
    return addr;
  }

  private String councilAddressPrivate() {
    return councilAddress();
  }

  /** Envoie une transaction (submit) au council. Nécessite une clé propriétaire (governance). */
  public String submitTransaction(String to, BigInteger value, String data) {
    String govPk = props.operatorPrivateKey();
    if (govPk == null || govPk.isBlank()) {
      throw new IllegalStateException("Governance key (blockchain.operator-private-key) not configured for MultiSig submit.");
    }
    Credentials credentials = Credentials.create(govPk.trim());
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId().longValue());

    DynamicBytes payload = data != null && !data.isBlank()
        ? new DynamicBytes(Numeric.hexStringToByteArray(data.startsWith("0x") ? data : "0x" + data))
        : new DynamicBytes(new byte[0]);
    Function fn = new Function(
        "submitTransaction",
        List.of(new Address(to), new Uint256(value != null ? value : BigInteger.ZERO), payload),
        List.of()
    );
    String encoded = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(300_000);
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, councilAddress(), encoded, BigInteger.ZERO);
      if (tx.hasError()) throw new IllegalStateException("submitTransaction failed: " + tx.getError().getMessage());
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("submitTransaction RPC error: " + e.getMessage(), e);
    }
  }

  public String confirmTransaction(BigInteger txId) {
    String govPk = props.operatorPrivateKey();
    if (govPk == null || govPk.isBlank()) {
      throw new IllegalStateException("Governance key not configured for MultiSig confirm.");
    }
    Credentials credentials = Credentials.create(govPk.trim());
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId().longValue());
    Function fn = new Function("confirmTransaction", List.of(new Uint256(txId)), List.of());
    String encoded = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(100_000);
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, councilAddress(), encoded, BigInteger.ZERO);
      if (tx.hasError()) throw new IllegalStateException("confirmTransaction failed: " + tx.getError().getMessage());
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("confirmTransaction RPC error: " + e.getMessage(), e);
    }
  }

  public String executeTransaction(BigInteger txId) {
    String govPk = props.operatorPrivateKey();
    if (govPk == null || govPk.isBlank()) {
      throw new IllegalStateException("Governance key not configured for MultiSig execute.");
    }
    Credentials credentials = Credentials.create(govPk.trim());
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId().longValue());
    Function fn = new Function("executeTransaction", List.of(new Uint256(txId)), List.of());
    String encoded = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();
    BigInteger gasLimit = BigInteger.valueOf(500_000);
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, councilAddress(), encoded, BigInteger.ZERO);
      if (tx.hasError()) throw new IllegalStateException("executeTransaction failed: " + tx.getError().getMessage());
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("executeTransaction RPC error: " + e.getMessage(), e);
    }
  }

  /** Détail d'une transaction (to, value, data, executed, confirmations). */
  public MultiSigTransactionDto getTransaction(BigInteger txId) {
    Function f = new Function(
        "transactions",
        List.of(new Uint256(txId)),
        List.of(
            new TypeReference<Address>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<org.web3j.abi.datatypes.DynamicBytes>() {},
            new TypeReference<Bool>() {},
            new TypeReference<Uint256>() {}
        )
    );
    List<Type> decoded = evm.ethCall(councilAddress(), f);
    if (decoded == null || decoded.size() < 5) return null;
    return new MultiSigTransactionDto(
        txId,
        ((Address) decoded.get(0)).getValue(),
        (BigInteger) decoded.get(1).getValue(),
        (byte[]) decoded.get(2).getValue(),
        (Boolean) decoded.get(3).getValue(),
        (BigInteger) decoded.get(4).getValue()
    );
  }

  public BigInteger getTransactionsCount() {
    Function f = new Function("transactionsCount", List.of(), List.of(new TypeReference<Uint256>() {}));
    List<Type> out = evm.ethCall(councilAddress(), f);
    return out != null && !out.isEmpty() ? (BigInteger) out.get(0).getValue() : BigInteger.ZERO;
  }

  /** Liste des propriétaires (signataires). Le contrat n'expose pas owners.length; on itère owners(0), owners(1), ... jusqu'à échec ou max 32. */
  public List<String> getOwners() {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 32; i++) {
      try {
        Function f = new Function("owners", List.of(new Uint256(BigInteger.valueOf(i))), List.of(new TypeReference<Address>() {}));
        List<Type> out = evm.ethCall(councilAddress(), f);
        if (out != null && !out.isEmpty()) {
          list.add(((Address) out.get(0)).getValue());
        } else {
          break;
        }
      } catch (Exception e) {
        break;
      }
    }
    return list;
  }

  /** Seuil de confirmations requis (N-of-M). */
  public BigInteger getThreshold() {
    Function f = new Function("threshold", List.of(), List.of(new TypeReference<Uint256>() {}));
    List<Type> out = evm.ethCall(councilAddress(), f);
    return out != null && !out.isEmpty() ? (BigInteger) out.get(0).getValue() : BigInteger.ZERO;
  }

  private BigInteger chainId() {
    try {
      return web3j.ethChainId().send().getChainId();
    } catch (IOException e) {
      throw new IllegalStateException("eth_chainId failed: " + e.getMessage(), e);
    }
  }

  private BigInteger suggestedGasPrice() {
    try {
      EthGasPrice gp = web3j.ethGasPrice().send();
      return gp.getGasPrice();
    } catch (IOException e) {
      return BigInteger.valueOf(20_000_000_000L);
    }
  }

  public record MultiSigTransactionDto(
      BigInteger txId,
      String to,
      BigInteger value,
      byte[] data,
      boolean executed,
      BigInteger confirmations
  ) {}
}
