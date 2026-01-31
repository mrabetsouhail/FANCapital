package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Dev/MVP helper: when KYC Level 1 is validated, bootstrap on-chain state:
 * - whitelist the user's WaaS wallet in KYCRegistry
 * - mint some on-chain TND to the wallet (to allow buys)
 * - approve pools to spend TND from the wallet
 *
 * This is ONLY for local testing.
 */
@Service
public class OnchainBootstrapService {
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L); // 1e8
  private static final BigInteger BOOTSTRAP_TND = BigInteger.valueOf(10_000).multiply(PRICE_SCALE);
  private static final BigInteger MAX_APPROVE = new BigInteger("2").pow(255); // big enough
  private static final BigInteger MIN_GAS_ETH = new BigInteger("10000000000000000"); // 0.01 ETH
  private static final BigInteger TOPUP_GAS_ETH = new BigInteger("50000000000000000"); // 0.05 ETH

  private final Web3j web3j;
  private final BlockchainProperties props;
  private final DeploymentRegistry registry;
  private final DeploymentInfraService infra;
  private final AppUserRepository userRepo;
  private final WaasUserWalletService waasWallets;

  public OnchainBootstrapService(
      Web3j web3j,
      BlockchainProperties props,
      DeploymentRegistry registry,
      DeploymentInfraService infra,
      AppUserRepository userRepo,
      WaasUserWalletService waasWallets
  ) {
    this.web3j = web3j;
    this.props = props;
    this.registry = registry;
    this.infra = infra;
    this.userRepo = userRepo;
    this.waasWallets = waasWallets;
  }

  @Transactional(readOnly = true)
  public void bootstrapUser(String userId) {
    AppUser u = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    String wallet = u.getWalletAddress();
    if (wallet == null || wallet.isBlank()) throw new IllegalStateException("User has no walletAddress");

    // 1) whitelist in KYCRegistry
    String kycRegistry = infra.kycRegistryAddress();
    Credentials operator = operatorCredentials();
    TransactionManager opTm = new RawTransactionManager(web3j, operator, chainId().longValue());

    Function wl = new Function(
        "addToWhitelist",
        List.of(new Address(wallet), new Uint8(BigInteger.valueOf(u.getKycLevel())), new Bool(u.isResident())),
        List.of()
    );
    sendTx(opTm, kycRegistry, wl, BigInteger.valueOf(250_000));

    // 1b) fund wallet with a small amount of native ETH for gas (to allow approve tx)
    BigInteger ethBal = ethBalanceOf(wallet);
    if (ethBal.compareTo(MIN_GAS_ETH) < 0) {
      sendValue(opTm, wallet, TOPUP_GAS_ETH, BigInteger.valueOf(21_000));
    }

    // 2) ensure some TND balance
    String cash = infra.cashTokenAddress();
    BigInteger bal = erc20BalanceOf(cash, wallet);
    if (bal.compareTo(BOOTSTRAP_TND) < 0) {
      BigInteger mintAmt = BOOTSTRAP_TND.subtract(bal);
      Function mint = new Function("mint", List.of(new Address(wallet), new Uint256(mintAmt)), List.of());
      sendTx(opTm, cash, mint, BigInteger.valueOf(250_000));
    }

    // 3) approve pools for spending user's TND (signed by user's WaaS key)
    Credentials userCreds = waasWallets.credentialsForUser(userId);
    TransactionManager userTm = new RawTransactionManager(web3j, userCreds, chainId().longValue());
    for (var fund : registry.listFunds()) {
      Function approve = new Function("approve", List.of(new Address(fund.pool()), new Uint256(MAX_APPROVE)), List.of());
      sendTx(userTm, cash, approve, BigInteger.valueOf(120_000));
    }
  }

  private Credentials operatorCredentials() {
    String pk = props.operatorPrivateKey();
    if (pk == null || pk.isBlank()) {
      throw new IllegalStateException("OPERATOR_PRIVATE_KEY not configured (blockchain.operator-private-key).");
    }
    return Credentials.create(pk.trim());
  }

  private BigInteger ethBalanceOf(String addr) {
    try {
      return web3j.ethGetBalance(addr, DefaultBlockParameterName.LATEST).send().getBalance();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getBalance failed: " + e.getMessage(), e);
    }
  }

  private BigInteger erc20BalanceOf(String token, String user) {
    Function f = new Function(
        "balanceOf",
        List.of(new Address(user)),
        List.of(new TypeReference<Uint256>() {})
    );
    String data = FunctionEncoder.encode(f);
    Transaction call = Transaction.createEthCallTransaction(null, token, data);
    try {
      EthCall res = web3j.ethCall(call, DefaultBlockParameterName.LATEST).send();
      if (res.hasError()) throw new IllegalStateException("eth_call error: " + res.getError().getMessage());
      var decoded = org.web3j.abi.FunctionReturnDecoder.decode(res.getValue(), f.getOutputParameters());
      return (BigInteger) decoded.get(0).getValue();
    } catch (IOException e) {
      throw new IllegalStateException("balanceOf eth_call failed: " + e.getMessage(), e);
    }
  }

  private void sendTx(TransactionManager tm, String to, Function fn, BigInteger gasLimit) {
    String data = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, to, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("EVM tx failed: " + tx.getError().getMessage());
      }
    } catch (IOException e) {
      throw new IllegalStateException("EVM tx RPC error: " + e.getMessage(), e);
    }
  }

  private void sendValue(TransactionManager tm, String to, BigInteger valueWei, BigInteger gasLimit) {
    BigInteger gasPrice = suggestedGasPrice();
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, gasLimit, to, "", valueWei);
      if (tx.hasError()) {
        throw new IllegalStateException("EVM tx failed: " + tx.getError().getMessage());
      }
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

