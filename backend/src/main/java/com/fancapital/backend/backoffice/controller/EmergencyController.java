package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.PanicKeyService;
import com.fancapital.backend.blockchain.model.TxDtos.TxResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller pour les actions d'urgence (Panic Button).
 * 
 * Selon le Livre Blanc Technique v3.0, la Panic Key permet d'arrêter
 * immédiatement toutes les transactions en cas d'intrusion ou de faille
 * de sécurité détectée.
 */
@RestController
@RequestMapping("/api/backoffice/emergency")
public class EmergencyController {
  private final PanicKeyService panicKeyService;
  private final BackofficeAuthzService authz;

  public EmergencyController(PanicKeyService panicKeyService, BackofficeAuthzService authz) {
    this.panicKeyService = panicKeyService;
    this.authz = authz;
  }

  public record PauseAllRequest(
      @NotBlank @Size(min = 10, max = 500) String reason
  ) {}

  /**
   * Active le Bouton Panique (pause globale de tous les contrats).
   * 
   * Cette action nécessite :
   * 1. ADMIN_EMAILS (authentification)
   * 2. PANIC_PRIVATE_KEY configurée (clé froide)
   * 
   * Une fois activée, toutes les opérations (mint, burn, transfer, buy, sell, P2P)
   * seront bloquées jusqu'à ce qu'un multi-sig (GOVERNANCE_ROLE) reprenne les opérations.
   */
  @PostMapping("/pause-all")
  public ResponseEntity<TxResponse> pauseAll(@RequestBody PauseAllRequest req) {
    // Require admin access
    authz.requireAdmin();
    
    try {
      String txHash = panicKeyService.pauseAll(req.reason());
      return ResponseEntity.ok(new TxResponse("submitted", txHash, "Global pause activated. All operations are now frozen."));
    } catch (IllegalStateException e) {
      if (e.getMessage().contains("PANIC_PRIVATE_KEY not configured")) {
        return ResponseEntity.status(503).body(new TxResponse("error", null, 
            "Panic Key not configured. This key must be stored in cold storage and configured via PANIC_PRIVATE_KEY environment variable."));
      }
      throw e;
    }
  }

  /**
   * Vérifie l'état de la pause globale.
   */
  // Note: Resume nécessite GOVERNANCE_ROLE (multi-sig), pas PANIC_KEY_ROLE
  // La reprise doit être effectuée via le service de gouvernance multi-sig
}
