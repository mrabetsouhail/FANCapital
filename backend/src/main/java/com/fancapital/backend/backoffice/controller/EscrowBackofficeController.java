package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.model.EscrowBackofficeDtos;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.EscrowBackofficeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur backoffice pour Escrow & Libération au Prorata.
 * - Registre des actifs bloqués (tokens isLocked / escrowLockedAmount)
 * - Suivi des remboursements (calendrier, déblocage progressif)
 */
@RestController
@RequestMapping("/api/backoffice/escrow")
public class EscrowBackofficeController {

  private final EscrowBackofficeService escrowService;
  private final BackofficeAuthzService authz;

  public EscrowBackofficeController(EscrowBackofficeService escrowService, BackofficeAuthzService authz) {
    this.escrowService = escrowService;
    this.authz = authz;
  }

  /** Registre des actifs bloqués par utilisateur (Atlas/Didon). */
  @GetMapping("/locked-assets")
  public EscrowBackofficeDtos.LockedAssetsResponse listLockedAssets() {
    authz.requireAuditRead();
    return escrowService.listLockedAssets();
  }

  /** Suivi des remboursements (calendrier, déblocage progressif). */
  @GetMapping("/repayment-tracking")
  public EscrowBackofficeDtos.RepaymentTrackingResponse listRepaymentTracking() {
    authz.requireAuditRead();
    return escrowService.listRepaymentTracking();
  }
}
