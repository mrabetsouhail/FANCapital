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
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import com.fancapital.backend.blockchain.model.TxDtos.TxResponse;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.LiquidityPoolWriteService;
import com.fancapital.backend.blockchain.service.OperatorDiagnosticsService;
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
  private final LiquidityPoolWriteService poolWriteService;
  private final OperatorDiagnosticsService operatorDiagnostics;

  public BlockchainController(
      DeploymentRegistry registry,
      BlockchainReadService readService,
      LiquidityPoolWriteService poolWriteService,
      OperatorDiagnosticsService operatorDiagnostics
  ) {
    this.registry = registry;
    this.readService = readService;
    this.poolWriteService = poolWriteService;
    this.operatorDiagnostics = operatorDiagnostics;
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
    return readService.portfolio(user);
  }

  @GetMapping("/investor/profile")
  public InvestorProfileResponse investorProfile(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String user) {
    return readService.investorProfile(user);
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

  @PostMapping("/p2p/settle")
  public TxResponse p2pSettle(@Valid @RequestBody P2PSettleRequest req) {
    return new TxResponse("submitted", "0xmocked", "Spring Boot MVP: p2p settle not wired to signer yet");
  }
}

