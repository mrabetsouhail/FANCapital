package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.backoffice.model.PlatformFeeWalletDtos;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.backoffice.service.PlatformFeeWalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur pour le wallet des frais de la plateforme.
 * Permet de visualiser les frais collectés pour une comptabilité transparente.
 */
@RestController
@RequestMapping("/api/backoffice/fees")
public class PlatformFeeWalletController {
  private final PlatformFeeWalletService feeWalletService;
  private final BackofficeAuthzService authz;

  public PlatformFeeWalletController(PlatformFeeWalletService feeWalletService, BackofficeAuthzService authz) {
    this.feeWalletService = feeWalletService;
    this.authz = authz;
  }

  /**
   * Récupère le dashboard des frais de la plateforme.
   * Accessible aux admins et aux rôles avec accès audit.
   */
  @GetMapping("/wallet")
  public ResponseEntity<PlatformFeeWalletDtos.FeeWalletDashboard> getFeeWallet() {
    authz.requireAuditRead();
    return ResponseEntity.ok(feeWalletService.dashboard());
  }
}
