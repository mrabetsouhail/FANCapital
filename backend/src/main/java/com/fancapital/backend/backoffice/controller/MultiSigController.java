package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.MultiSigService;
import com.fancapital.backend.backoffice.service.MultiSigService.MultiSigTransactionDto;
import com.fancapital.backend.blockchain.model.TxDtos.TxResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST pour la gouvernance Multi-Sig (MultiSigCouncil).
 * 
 * Permet aux administrateurs de :
 * - Soumettre des propositions de transaction
 * - Confirmer des propositions (si signataire)
 * - Exécuter des propositions (quand seuil atteint)
 * - Consulter les propositions et le statut du council
 */
@RestController
@RequestMapping("/api/backoffice/multisig")
public class MultiSigController {
  private static final String ETH_ADDRESS_RX = "^0x[a-fA-F0-9]{40}$";
  private static final String HEX_DATA_RX = "^0x[a-fA-F0-9]*$";

  private final MultiSigService multiSigService;
  private final BackofficeAuthzService authz;

  public MultiSigController(MultiSigService multiSigService, BackofficeAuthzService authz) {
    this.multiSigService = multiSigService;
    this.authz = authz;
  }

  public record SubmitTransactionRequest(
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String to,
      @NotBlank String value,
      @Pattern(regexp = HEX_DATA_RX) String data
  ) {}

  public record MultiSigInfoResponse(
      String councilAddress,
      List<String> owners,
      BigInteger threshold,
      BigInteger totalOwners,
      BigInteger transactionsCount
  ) {}

  public record MultiSigTransactionResponse(
      BigInteger txId,
      String to,
      String value,
      String data,
      boolean executed,
      BigInteger confirmations,
      BigInteger threshold,
      boolean canExecute
  ) {}

  public record MultiSigTransactionsListResponse(
      List<MultiSigTransactionResponse> transactions
  ) {}

  /**
   * Informations sur le MultiSigCouncil (owners, threshold, nombre de transactions).
   */
  @GetMapping("/info")
  public ResponseEntity<MultiSigInfoResponse> getInfo() {
    authz.requireAdmin();
    try {
      List<String> owners = multiSigService.getOwners();
      BigInteger threshold = multiSigService.getThreshold();
      BigInteger count = multiSigService.getTransactionsCount();
      String councilAddress = multiSigService.councilAddress();
      return ResponseEntity.ok(new MultiSigInfoResponse(
          councilAddress,
          owners,
          threshold,
          BigInteger.valueOf(owners.size()),
          count
      ));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get MultiSig info: " + e.getMessage(), e);
    }
  }

  /**
   * Liste toutes les transactions (propositions) du council.
   */
  @GetMapping("/transactions")
  public ResponseEntity<MultiSigTransactionsListResponse> listTransactions() {
    authz.requireAdmin();
    try {
      BigInteger count = multiSigService.getTransactionsCount();
      List<MultiSigTransactionResponse> txs = new java.util.ArrayList<>();
      BigInteger threshold = multiSigService.getThreshold();
      
      for (BigInteger i = BigInteger.ZERO; i.compareTo(count) < 0; i = i.add(BigInteger.ONE)) {
        MultiSigTransactionDto tx = multiSigService.getTransaction(i);
        if (tx != null) {
          txs.add(new MultiSigTransactionResponse(
              tx.txId(),
              tx.to(),
              tx.value().toString(),
              tx.data() != null ? "0x" + java.util.HexFormat.of().formatHex(tx.data()) : "0x",
              tx.executed(),
              tx.confirmations(),
              threshold,
              !tx.executed() && tx.confirmations().compareTo(threshold) >= 0
          ));
        }
      }
      
      return ResponseEntity.ok(new MultiSigTransactionsListResponse(txs));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to list transactions: " + e.getMessage(), e);
    }
  }

  /**
   * Détails d'une transaction spécifique.
   */
  @GetMapping("/transactions/{txId}")
  public ResponseEntity<MultiSigTransactionResponse> getTransaction(@PathVariable String txId) {
    authz.requireAdmin();
    try {
      BigInteger txIdBig = new BigInteger(txId);
      MultiSigTransactionDto tx = multiSigService.getTransaction(txIdBig);
      if (tx == null) {
        return ResponseEntity.notFound().build();
      }
      BigInteger threshold = multiSigService.getThreshold();
      return ResponseEntity.ok(new MultiSigTransactionResponse(
          tx.txId(),
          tx.to(),
          tx.value().toString(),
          tx.data() != null ? "0x" + java.util.HexFormat.of().formatHex(tx.data()) : "0x",
          tx.executed(),
          tx.confirmations(),
          threshold,
          !tx.executed() && tx.confirmations().compareTo(threshold) >= 0
      ));
    } catch (NumberFormatException e) {
      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get transaction: " + e.getMessage(), e);
    }
  }

  /**
   * Soumet une nouvelle proposition de transaction au council.
   * 
   * @param req Contient: to (adresse destination), value (montant en wei, string), data (calldata hex)
   */
  @PostMapping("/submit")
  public ResponseEntity<TxResponse> submitTransaction(@RequestBody SubmitTransactionRequest req) {
    authz.requireAdmin();
    try {
      BigInteger value = new BigInteger(req.value());
      String data = req.data() != null && !req.data().isBlank() ? req.data() : "0x";
      String txHash = multiSigService.submitTransaction(req.to(), value, data);
      return ResponseEntity.ok(new TxResponse("submitted", txHash, 
          "Transaction proposal submitted. Waiting for confirmations."));
    } catch (NumberFormatException e) {
      return ResponseEntity.badRequest().body(new TxResponse("error", null, 
          "Invalid value format. Must be a number (wei)."));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(new TxResponse("error", null, 
          "Failed to submit transaction: " + e.getMessage()));
    }
  }

  /**
   * Confirme une proposition existante (si l'utilisateur est un signataire).
   */
  @PostMapping("/confirm/{txId}")
  public ResponseEntity<TxResponse> confirmTransaction(@PathVariable String txId) {
    authz.requireAdmin();
    try {
      BigInteger txIdBig = new BigInteger(txId);
      String txHash = multiSigService.confirmTransaction(txIdBig);
      return ResponseEntity.ok(new TxResponse("confirmed", txHash, 
          "Transaction confirmed. Check if threshold is reached to execute."));
    } catch (NumberFormatException e) {
      return ResponseEntity.badRequest().body(new TxResponse("error", null, 
          "Invalid transaction ID format."));
    } catch (IllegalStateException e) {
      if (e.getMessage().contains("not configured")) {
        return ResponseEntity.status(503).body(new TxResponse("error", null, 
            "Governance key not configured. Cannot confirm transaction."));
      }
      return ResponseEntity.status(500).body(new TxResponse("error", null, 
          "Failed to confirm transaction: " + e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(new TxResponse("error", null, 
          "Failed to confirm transaction: " + e.getMessage()));
    }
  }

  /**
   * Exécute une proposition qui a atteint le seuil de confirmations.
   */
  @PostMapping("/execute/{txId}")
  public ResponseEntity<TxResponse> executeTransaction(@PathVariable String txId) {
    authz.requireAdmin();
    try {
      BigInteger txIdBig = new BigInteger(txId);
      String txHash = multiSigService.executeTransaction(txIdBig);
      return ResponseEntity.ok(new TxResponse("executed", txHash, 
          "Transaction executed successfully."));
    } catch (NumberFormatException e) {
      return ResponseEntity.badRequest().body(new TxResponse("error", null, 
          "Invalid transaction ID format."));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(new TxResponse("error", null, 
          "Failed to execute transaction: " + e.getMessage()));
    }
  }
}
