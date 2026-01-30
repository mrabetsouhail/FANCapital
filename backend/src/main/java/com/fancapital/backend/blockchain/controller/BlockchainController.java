package com.fancapital.backend.blockchain.controller;

import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.blockchain.model.FundsListResponse;
import com.fancapital.backend.blockchain.model.OracleVniResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellResponse;
import com.fancapital.backend.blockchain.model.TxDtos.BuyRequest;
import com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest;
import com.fancapital.backend.blockchain.model.TxDtos.SellRequest;
import com.fancapital.backend.blockchain.model.TxDtos.TxResponse;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
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

  public BlockchainController(DeploymentRegistry registry, BlockchainReadService readService) {
    this.registry = registry;
    this.readService = readService;
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

  @GetMapping("/oracle/vni")
  public OracleVniResponse getVni(@RequestParam @Pattern(regexp = ETH_ADDRESS_RX) String token) {
    return readService.getVni(token);
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
    return new TxResponse("submitted", "0xmocked", "Spring Boot MVP: buy() not wired to signer yet");
  }

  @PostMapping("/pool/sell")
  public TxResponse sell(@Valid @RequestBody SellRequest req) {
    return new TxResponse("submitted", "0xmocked", "Spring Boot MVP: sell() not wired to signer yet");
  }

  @PostMapping("/p2p/settle")
  public TxResponse p2pSettle(@Valid @RequestBody P2PSettleRequest req) {
    return new TxResponse("submitted", "0xmocked", "Spring Boot MVP: p2p settle not wired to signer yet");
  }
}

