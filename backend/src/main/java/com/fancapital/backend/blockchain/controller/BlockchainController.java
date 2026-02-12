package com.fancapital.backend.blockchain.controller;

import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.blockchain.model.FundsListResponse;
import com.fancapital.backend.blockchain.model.InvestorProfileDtos.InvestorProfileResponse;
import com.fancapital.backend.blockchain.model.OracleVniResponse;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellResponse;
import com.fancapital.backend.blockchain.model.TxHistoryDtos.TxHistoryResponse;
import com.fancapital.backend.blockchain.model.TxDtos.AdvanceRequest;
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import com.fancapital.backend.blockchain.model.TxDtos.TxResponse;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import com.fancapital.backend.blockchain.service.CreditAdvanceActivationService;
import com.fancapital.backend.blockchain.service.CreditAdvanceRequestService;
import com.fancapital.backend.blockchain.service.CreditReadService;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.LiquidityPoolWriteService;
import java.math.BigInteger;
import java.util.List;
import com.fancapital.backend.blockchain.service.P2PExchangeWriteService;
import com.fancapital.backend.blockchain.service.OnchainBootstrapService;
import com.fancapital.backend.blockchain.service.OperatorDiagnosticsService;
import com.fancapital.backend.blockchain.service.SciScorePushService;
import com.fancapital.backend.blockchain.service.SciScoreService;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {
  private static final String ETH_ADDRESS_RX = "^0x[a-fA-F0-9]{40}$";
  private final DeploymentRegistry registry;
  private final BlockchainReadService readService;
  private final CreditAdvanceRequestService creditAdvanceRequest;
  private final CreditAdvanceActivationService creditAdvanceActivation;
  private final CreditReadService creditRead;
  private final LiquidityPoolWriteService poolWriteService;
  private final P2PExchangeWriteService p2pWriteService;
  private final OperatorDiagnosticsService operatorDiagnostics;
  private final SciScoreService sciScore;
  private final SciScorePushService sciPush;
  private final OnchainBootstrapService onchainBootstrap;

  public BlockchainController(
      DeploymentRegistry registry,
      BlockchainReadService readService,
      CreditAdvanceRequestService creditAdvanceRequest,
      CreditAdvanceActivationService creditAdvanceActivation,
      CreditReadService creditRead,
      LiquidityPoolWriteService poolWriteService,
      P2PExchangeWriteService p2pWriteService,
      OperatorDiagnosticsService operatorDiagnostics,
      SciScoreService sciScore,
      SciScorePushService sciPush,
      OnchainBootstrapService onchainBootstrap
  ) {
    this.registry = registry;
    this.readService = readService;
    this.creditAdvanceRequest = creditAdvanceRequest;
    this.creditAdvanceActivation = creditAdvanceActivation;
    this.creditRead = creditRead;
    this.poolWriteService = poolWriteService;
    this.p2pWriteService = p2pWriteService;
    this.operatorDiagnostics = operatorDiagnostics;
    this.sciScore = sciScore;
    this.sciPush = sciPush;
    this.onchainBootstrap = onchainBootstrap;
  }

  @GetMapping("/funds")
  public FundsListResponse listFunds() {
    return new FundsListResponse(registry.listFunds());
  }

  @GetMapping("/funds/{id}")
  public ResponseEntity<FundDto> getFund(@PathVariable int id) {
    return registry.getFund(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  // Simple debug endpoint
  @GetMapping("/_meta")
  public Map<String, Object> meta(@RequestParam(required = false) String token) {
    return Map.of(
        "deploymentsPath", registry.getDeploymentsPathUsed(),
        "fundForToken", token == null ? null : registry.findByToken(token).orElse(null)
    );
  }

  @GetMapping("/_operator")
  public Object operator() {
    return operatorDiagnostics.info();
  }

  @GetMapping("/oracle/vni")
  public OracleVniResponse getVni(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String token) {
    return readService.getVni(token);
  }

  @GetMapping("/portfolio")
  public PortfolioResponse portfolio(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    PortfolioResponse p = readService.portfolio(user);
    // Auto-alimentation Cash Wallet si solde = 0 (dev/test jusqu'à intégration virements)
    if ("0".equals(p.cashBalanceTnd()) || (p.cashBalanceTnd() != null && p.cashBalanceTnd().trim().equals("0"))) {
      try {
        long amount = 10_000L;
        if (p.creditLineTnd() != null && !p.creditLineTnd().equals("0")) {
          long credit1e8 = Long.parseLong(p.creditLineTnd());
          if (credit1e8 >= 1_000_000_000_000L) amount = 10_000L;
          else if (credit1e8 >= 500_000_000_000L) amount = 5_000L;
        }
        onchainBootstrap.seedCashToWallet(user, amount);
        return readService.portfolio(user);
      } catch (Exception e) {
        // ignore seed errors (ex: MINTER_ROLE manquant), return initial response
      }
    }
    return p;
  }

  @GetMapping("/investor/profile")
  public InvestorProfileResponse investorProfile(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    return readService.investorProfile(user);
  }

  /** SCI v4.5: calcul du score et du tier effectif (loi du minimum). */
  @GetMapping("/investor/sci")
  public SciScoreService.SciScoreResult sciScore(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    return sciScore.computeScore(user);
  }

  /** SCI v4.5: push score + tier effectif vers la blockchain. */
  @PostMapping("/investor/sci/push")
  public SciScorePushService.SciPushResult sciPush(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    return sciPush.pushForWallet(user);
  }

  /**
   * Alimente la Cash Wallet (dev/test). Mint TND on-chain pour nouveaux utilisateurs.
   * Ex: POST /api/blockchain/seed/cash?user=0x...&amount=5000
   * L'opérateur doit avoir MINTER_ROLE sur CashTokenTND.
   */
  @PostMapping("/seed/cash")
  public Map<String, Object> seedCash(
      @RequestParam(name = "user") @Pattern(regexp = ETH_ADDRESS_RX) String walletAddress,
      @RequestParam(required = false, defaultValue = "5000") int amount
  ) {
    String result = onchainBootstrap.seedCashToWallet(walletAddress, amount);
    return Map.of("status", "ok", "message", result != null ? result : "done");
  }

  @GetMapping("/tx/history")
  public TxHistoryResponse txHistory(
      @RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user,
      @RequestParam(required = false, defaultValue = "150") int limit
  ) {
    return readService.txHistory(user, limit);
  }

  @PostMapping("/pool/quote-buy")
  public QuoteBuyResponse quoteBuy(@Valid @RequestBody QuoteBuyRequest req) {
    return readService.quoteBuy(req);
  }

  @PostMapping("/pool/quote-sell")
  public QuoteSellResponse quoteSell(@Valid @RequestBody QuoteSellRequest req) {
    return readService.quoteSell(req);
  }

  // ---- Execution endpoints (stubs until we implement signing + role separation) ----
  /**
   * Avance active de l'utilisateur (pour afficher le calendrier réel).
   */
  @GetMapping("/advance/active")
  public ResponseEntity<CreditReadService.LoanInfo> getActiveAdvance(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    CreditReadService.LoanInfo loan = creditRead.getActiveLoanForUser(user);
    return loan != null ? ResponseEntity.ok(loan) : ResponseEntity.notFound().build();
  }

  /**
   * Liste des demandes d'avance en attente (opérateur).
   */
  @GetMapping("/advance/requested")
  public List<CreditReadService.LoanInfo> listRequestedAdvances() {
    return creditRead.listRequestedLoans();
  }

  /**
   * Active une avance : crédite le Credit Wallet (mint TND) puis lock collatéral (opérateur).
   * Ex: POST /api/blockchain/advance/activate?loanId=1
   */
  @PostMapping("/advance/activate")
  public TxResponse activateAdvance(@RequestParam long loanId) {
    String txHash = creditAdvanceActivation.activateAndCredit(BigInteger.valueOf(loanId));
    return new TxResponse("submitted", txHash, "Avance activée. Credit Wallet crédité, collatéral verrouillé.");
  }

  /**
   * Demande d'avance sur titres (AST). L'utilisateur signe via WaaS.
   * Token: Atlas ou Didon. Après validation, l'opérateur appelle activateAdvance pour créditer le Credit Wallet.
   */
  @PostMapping("/advance/request")
  public TxResponse requestAdvance(@Valid @RequestBody AdvanceRequest req) {
    String tokenAddr = registry.getTokenAddressForSymbol(req.token());
    if (tokenAddr == null || tokenAddr.isBlank()) {
      throw new IllegalArgumentException("Token invalide: " + req.token() + ". Utilisez Atlas ou Didon.");
    }
    String txHash = creditAdvanceRequest.requestAdvanceForUser(
        req.user(), tokenAddr, req.collateralAmount(), req.durationDays());
    return new TxResponse("submitted", txHash, "Demande d'avance soumise. En attente d'activation par l'opérateur.");
  }

  @PostMapping("/pool/buy")
  public TxResponse buy(@Valid @RequestBody BuyRequest req) {
    String txHash = poolWriteService.buyFor(req);
    return new TxResponse("submitted", txHash, "LiquidityPool.buyFor submitted");
  }

  @PostMapping("/pool/sell")
  public TxResponse sell(@Valid @RequestBody SellRequest req) {
    String txHash = poolWriteService.sellFor(req);
    return new TxResponse("submitted", txHash, "LiquidityPool.sellFor submitted");
  }

  /**
   * @deprecated Use /api/blockchain/p2p/order instead for order book matching.
   * This endpoint is kept for direct settlement (when both parties are known).
   */
  @PostMapping("/p2p/settle")
  public TxResponse p2pSettle(@Valid @RequestBody P2PSettleRequest req) {
    String txHash = p2pWriteService.settle(req);
    return new TxResponse("submitted", txHash, "P2PExchange.settle submitted");
  }
}

