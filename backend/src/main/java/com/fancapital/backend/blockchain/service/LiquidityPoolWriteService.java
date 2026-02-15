package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.Notification;
import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.auth.service.NotificationService;
import com.fancapital.backend.backoffice.audit.service.BusinessContextService;
import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

@Service
public class LiquidityPoolWriteService {
  private static final Logger log = LoggerFactory.getLogger(LiquidityPoolWriteService.class);
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L); // 1e8
  private static final BigInteger MAX_APPROVE = new BigInteger("2").pow(255);
  private static final BigInteger MIN_GAS_ETH = new BigInteger("10000000000000000");   // 0.01 ETH
  private static final BigInteger TOPUP_GAS_ETH = new BigInteger("50000000000000000");  // 0.05 ETH

  private final Web3j web3j;
  private final DeploymentRegistry registry;
  private final DeploymentInfraService infra;
  private final BlockchainProperties props;
  private final BusinessContextService businessContextService;
  private final AppUserRepository userRepo;
  private final WaasUserWalletService waasWallets;
  private final NotificationService notificationService;
  private final SciScorePushService sciPush;

  public LiquidityPoolWriteService(
      Web3j web3j,
      DeploymentRegistry registry,
      DeploymentInfraService infra,
      BlockchainProperties props,
      BusinessContextService businessContextService,
      AppUserRepository userRepo,
      WaasUserWalletService waasWallets,
      NotificationService notificationService,
      SciScorePushService sciPush
  ) {
    this.web3j = web3j;
    this.registry = registry;
    this.infra = infra;
    this.props = props;
    this.businessContextService = businessContextService;
    this.userRepo = userRepo;
    this.waasWallets = waasWallets;
    this.notificationService = notificationService;
    this.sciPush = sciPush;
  }

  public String buyFor(BuyRequest req) {
    var fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown fund token: " + req.token()));
    String pool = fund.pool();

    BigInteger tndIn = parseUint(req.tndIn(), "tndIn");

    // S'assurer que l'utilisateur a approve le pool pour dépenser ses TND (évite ERC20InsufficientAllowance)
    ensureCashAllowance(req.user(), pool, tndIn);

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

    userRepo.findByWalletAddressIgnoreCase(req.user()).ifPresent(u -> {
      double tnd = tndIn.doubleValue() / 100_000_000.0;
      String fundName = fund.name() != null ? fund.name() : "CPEF";
      try {
        notificationService.create(u.getId(), Notification.Type.PRICE,
            "Achat effectué - " + fundName,
            String.format("Vous avez acheté des tokens %s pour %.2f TND.", fundName, tnd),
            Priority.LOW);
      } catch (Exception ex) {
        System.err.println("Failed to create buy notification: " + ex.getMessage());
      }
    });

    // Mise à jour temps réel du score SCI (async, sans bloquer la réponse)
    scheduleScoreUpdate(req.user());

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

    userRepo.findByWalletAddressIgnoreCase(req.user()).ifPresent(u -> {
      String fundName = fund.name() != null ? fund.name() : "CPEF";
      try {
        notificationService.create(u.getId(), Notification.Type.PRICE,
            "Vente effectuée - " + fundName,
            String.format("Vous avez vendu des tokens %s.", fundName),
            Priority.LOW);
      } catch (Exception ex) {
        System.err.println("Failed to create sell notification: " + ex.getMessage());
      }
    });

    // Mise à jour temps réel du score SCI (async, sans bloquer la réponse)
    scheduleScoreUpdate(req.user());

    return txHash;
  }

  /** Lance le push du score SCI en arrière-plan après achat/vente. */
  private void scheduleScoreUpdate(String walletAddress) {
    CompletableFuture.runAsync(() -> {
      try {
        sciPush.pushForWallet(walletAddress);
        log.debug("SCI score pushed for {}", walletAddress);
      } catch (Exception e) {
        log.warn("SCI score push failed for {}: {}", walletAddress, e.getMessage());
      }
    });
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

  /** Vérifie que l'utilisateur a accordé au pool le droit de dépenser tndAmount TND. Si non, approve via WaaS. */
  private void ensureCashAllowance(String userAddress, String poolAddress, BigInteger tndAmount) {
    String cash = infra.cashTokenAddress();
    if (cash == null || cash.isBlank()) return;
    BigInteger allowance = allowanceOf(cash, userAddress, poolAddress);
    if (allowance.compareTo(tndAmount) >= 0) return;
    var userOpt = userRepo.findByWalletAddressIgnoreCase(userAddress);
    if (userOpt.isEmpty()) {
      throw new IllegalStateException(
          "ERC20InsufficientAllowance: User " + userAddress + " must approve pool " + poolAddress + ". " +
          "User not found in DB (WaaS) - ensure KYC validated and wallet provisioned."
      );
    }
    // Alimenter le wallet en ETH pour le gas si nécessaire (circuit fermé : la plateforme paie)
    ensureUserHasGasForTx(userAddress);

    try {
      Credentials creds = waasWallets.credentialsForUser(userOpt.get().getId());
      TransactionManager tm = new RawTransactionManager(web3j, creds, chainId().longValue());
      Function approve = new Function("approve", List.of(new Address(poolAddress), new Uint256(MAX_APPROVE)), List.of());
      String data = FunctionEncoder.encode(approve);
      BigInteger gasPrice = suggestedGasPrice();
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, BigInteger.valueOf(120_000), cash, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("approve failed: " + tx.getError().getMessage());
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Could not approve pool for user " + userAddress + ": " + e.getMessage(), e);
    }
  }

  /** Alimente le wallet utilisateur en ETH natif si solde insuffisant pour le gas (plateforme paie). */
  private void ensureUserHasGasForTx(String userAddress) {
    String opPk = props.operatorPrivateKey();
    if (opPk == null || opPk.isBlank()) return;
    try {
      BigInteger ethBal = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send().getBalance();
      if (ethBal.compareTo(MIN_GAS_ETH) >= 0) return;
      Credentials op = Credentials.create(opPk.trim());
      TransactionManager opTm = new RawTransactionManager(web3j, op, chainId().longValue());
      BigInteger gasPrice = suggestedGasPrice();
      EthSendTransaction tx = (EthSendTransaction) opTm.sendTransaction(gasPrice, BigInteger.valueOf(21_000), userAddress, "", TOPUP_GAS_ETH);
      if (tx.hasError()) {
        System.err.println("Gas topup failed for " + userAddress + ": " + tx.getError().getMessage());
        return;
      }
    } catch (Exception e) {
      System.err.println("Gas topup error for " + userAddress + ": " + e.getMessage());
    }
  }

  private BigInteger allowanceOf(String token, String owner, String spender) {
    Function f = new Function("allowance", List.of(new Address(owner), new Address(spender)), List.of(new TypeReference<Uint256>() {}));
    String data = FunctionEncoder.encode(f);
    Transaction call = Transaction.createEthCallTransaction(null, token, data);
    try {
      EthCall res = web3j.ethCall(call, DefaultBlockParameterName.LATEST).send();
      if (res.hasError() || res.getValue() == null || res.getValue().isBlank()) return BigInteger.ZERO;
      @SuppressWarnings("rawtypes")
      List<Type> decoded = FunctionReturnDecoder.decode(res.getValue(), f.getOutputParameters());
      return decoded.isEmpty() ? BigInteger.ZERO : (BigInteger) decoded.get(0).getValue();
    } catch (IOException e) {
      return BigInteger.ZERO;
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

