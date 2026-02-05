package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.backoffice.audit.service.BusinessContextService;
import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.blockchain.model.TxDtos.P2PSettleRequest;
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
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

@Service
public class P2PExchangeWriteService {
  private final Web3j web3j;
  private final DeploymentInfraService infra;
  private final BlockchainProperties props;
  private final BusinessContextService businessContextService;

  public P2PExchangeWriteService(
      Web3j web3j,
      DeploymentInfraService infra,
      BlockchainProperties props,
      BusinessContextService businessContextService
  ) {
    this.web3j = web3j;
    this.infra = infra;
    this.props = props;
    this.businessContextService = businessContextService;
  }

  /**
   * Settle a P2P trade between buyer and seller.
   * 
   * Note: For MVP, the frontend may send orders with buyer=0x0 or seller=0x0 (unmatched orders).
   * In a full implementation, a matching engine would pair orders before calling settle().
   * 
   * @param req P2P settlement request with token, seller, buyer, tokenAmount, and pricePerToken
   * @return Transaction hash
   */
  public String settle(P2PSettleRequest req) {
    String p2pExchangeAddress = infra.p2pExchangeAddress();
    if (p2pExchangeAddress == null || p2pExchangeAddress.isBlank()) {
      throw new IllegalStateException("P2PExchange address not found in deployments. Ensure P2PExchange is deployed.");
    }

    // Validate that both buyer and seller are provided (not zero addresses)
    String zeroAddress = "0x0000000000000000000000000000000000000000";
    if (req.buyer() == null || req.buyer().equalsIgnoreCase(zeroAddress)) {
      throw new IllegalArgumentException("P2P settle requires a valid buyer address. This appears to be an unmatched order. A matching engine is required to pair orders before settlement.");
    }
    if (req.seller() == null || req.seller().equalsIgnoreCase(zeroAddress)) {
      throw new IllegalArgumentException("P2P settle requires a valid seller address. This appears to be an unmatched order. A matching engine is required to pair orders before settlement.");
    }

    BigInteger tokenAmount = parseUint(req.tokenAmount(), "tokenAmount");
    BigInteger pricePerToken = parseUint(req.pricePerToken(), "pricePerToken");

    // P2PExchange.settle(address token, address seller, address buyer, uint256 tokenAmount, uint256 pricePerToken)
    Function fn = new Function(
        "settle",
        List.of(
            new Address(req.token()),
            new Address(req.seller()),
            new Address(req.buyer()),
            new Uint256(tokenAmount),
            new Uint256(pricePerToken)
        ),
        List.of()
    );

    // P2P transactions may require more gas due to multiple transfers (cash + tokens)
    String txHash = send(p2pExchangeAddress, fn, BigInteger.valueOf(1_500_000));
    
    // Enregistrer le BusinessContextId pour la traçabilité (Livre Blanc v2.1 - Section 4.2)
    String businessContextId = businessContextService.generateBusinessContextId("P2P_SETTLE");
    try {
      businessContextService.registerTransaction(
          txHash,
          businessContextId,
          p2pExchangeAddress,
          "P2P_SETTLE",
          String.format("P2P trade: seller %s -> buyer %s, token %s, amount %s, price %s", 
              req.seller(), req.buyer(), req.token(), tokenAmount, pricePerToken),
          null // accountingDocumentId sera ajouté par le backoffice si nécessaire
      );
    } catch (Exception e) {
      // Log l'erreur mais ne fait pas échouer la transaction
      System.err.println("Failed to register business context for P2P transaction " + txHash + ": " + e.getMessage());
    }
    
    return txHash;
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
