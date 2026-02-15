package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.blockchain.model.OrderBookDtos.Order;
import com.fancapital.backend.blockchain.model.OrderBookDtos.OrderSide;
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import java.math.BigInteger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Exécute le fallback d'un ordre P2P non matché vers la piscine de liquidité.
 * Utilise OrderFallbackExecutor si déployé, sinon délègue à LiquidityPoolWriteService.
 */
@Service
public class OrderFallbackExecutorService {
  private static final Logger log = LoggerFactory.getLogger(OrderFallbackExecutorService.class);
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);

  private final Web3j web3j;
  private final DeploymentInfraService infra;
  private final DeploymentRegistry registry;
  private final LiquidityPoolWriteService poolWrite;
  private final com.fancapital.backend.config.BlockchainProperties props;
  private final SciScorePushService sciPush;
  private final com.fancapital.backend.auth.repo.AppUserRepository userRepo;
  private final com.fancapital.backend.auth.service.NotificationService notificationService;

  public OrderFallbackExecutorService(
      Web3j web3j,
      DeploymentInfraService infra,
      DeploymentRegistry registry,
      LiquidityPoolWriteService poolWrite,
      com.fancapital.backend.config.BlockchainProperties props,
      SciScorePushService sciPush,
      com.fancapital.backend.auth.repo.AppUserRepository userRepo,
      com.fancapital.backend.auth.service.NotificationService notificationService
  ) {
    this.web3j = web3j;
    this.infra = infra;
    this.registry = registry;
    this.poolWrite = poolWrite;
    this.props = props;
    this.sciPush = sciPush;
    this.userRepo = userRepo;
    this.notificationService = notificationService;
  }

  /**
   * Exécute le reliquat de l'ordre vers la piscine.
   *
   * @param order  Ordre PENDING expiré (reliquat = tokenAmount - filledTokenAmount)
   * @return txHash ou null si erreur
   */
  public String executeFallback(Order order) {
    return executeFallbackWithCorrectAmounts(order);
  }

  private String executeViaContract(String executorAddr, String token, String user,
      BigInteger amount, boolean isBuy, String orderId) {
    // executeFallbackToPool(token, user, amount, isBuy, orderId)
    // For isBuy: amount = tndIn (notional). For sell: amount = tokenAmount.
    Function fn = new Function(
        "executeFallbackToPool",
        List.of(
            new Address(token),
            new Address(user),
            new Uint256(amount),
            new Bool(isBuy),
            new Utf8String(orderId)
        ),
        List.of()
    );
    String pk = props.operatorPrivateKey();
    if (pk == null || pk.isBlank()) {
      throw new IllegalStateException("OPERATOR_PRIVATE_KEY not configured");
    }
    Credentials creds = Credentials.create(pk.trim());
    TransactionManager tm = new RawTransactionManager(web3j, creds, chainId().longValue());
    String data = FunctionEncoder.encode(fn);
    BigInteger gasPrice = suggestedGasPrice();
    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(
          gasPrice, BigInteger.valueOf(1_500_000), executorAddr, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("Fallback tx failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (Exception e) {
      log.error("OrderFallbackExecutor executeFallbackToPool failed: {}", e.getMessage());
      throw new IllegalStateException("Fallback execution failed: " + e.getMessage(), e);
    }
  }

  private String executeViaPool(String token, String user, BigInteger amount, boolean isBuy, Order order) {
    if (isBuy) {
      // amount is tokenAmount here; we need tndIn = amount * price / 1e8
      BigInteger price = new BigInteger(order.pricePerToken());
      BigInteger tndIn = amount.multiply(price).divide(PRICE_SCALE);
      if (tndIn.signum() <= 0) {
        log.warn("Fallback buy: tndIn=0 for order {}", order.orderId());
        return null;
      }
      BuyRequest req = new BuyRequest(token, user, tndIn.toString());
      return poolWrite.buyFor(req);
    } else {
      SellRequest req = new SellRequest(token, user, amount.toString());
      return poolWrite.sellFor(req);
    }
  }

  /** Pour executeViaContract: isBuy=true ⇒ amount=tndIn (pool.buyFor). isBuy=false ⇒ amount=tokenAmount. */
  public String executeFallbackWithCorrectAmounts(Order order) {
    BigInteger total = new BigInteger(order.tokenAmount());
    BigInteger filled = new BigInteger(order.filledTokenAmount());
    BigInteger remainder = total.subtract(filled);
    if (remainder.signum() <= 0) return null;

    String token = order.token();
    String user = order.maker();
    boolean isBuy = order.side() == OrderSide.BUY;

    String executorAddr = infra.orderFallbackExecutorAddress();
    if (executorAddr != null && !executorAddr.isBlank()) {
      BigInteger amountForContract;
      if (isBuy) {
        BigInteger price = new BigInteger(order.pricePerToken());
        amountForContract = remainder.multiply(price).divide(PRICE_SCALE);
      } else {
        amountForContract = remainder;
      }
      if (amountForContract.signum() <= 0) return null;
      return executeViaContract(executorAddr, token, user, amountForContract, isBuy, order.orderId());
    }

    return executeViaPool(token, user, remainder, isBuy, order);
  }

  private BigInteger suggestedGasPrice() {
    try {
      var gp = web3j.ethGasPrice().send();
      if (gp.hasError()) return BigInteger.valueOf(1_000_000_000L);
      return gp.getGasPrice().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (Exception e) {
      return BigInteger.valueOf(1_000_000_000L);
    }
  }

  private BigInteger chainId() {
    try {
      return web3j.ethChainId().send().getChainId();
    } catch (Exception e) {
      return BigInteger.valueOf(31337);
    }
  }

  public void notifyAndPushScore(String walletAddress, String fundName, boolean isBuy) {
    userRepo.findByWalletAddressIgnoreCase(walletAddress).ifPresent(u -> {
      try {
        notificationService.create(u.getId(),
            com.fancapital.backend.auth.model.Notification.Type.PRICE,
            "Ordre P2P — fallback exécuté",
            String.format("Le reliquat de votre ordre %s a été exécuté via la piscine de liquidité.", fundName),
            com.fancapital.backend.auth.model.Notification.Priority.LOW);
      } catch (Exception ex) {
        log.warn("Failed to create fallback notification: {}", ex.getMessage());
      }
    });
    java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        sciPush.pushForWallet(walletAddress);
      } catch (Exception e) {
        log.warn("SCI push after fallback failed: {}", e.getMessage());
      }
    });
  }
}
