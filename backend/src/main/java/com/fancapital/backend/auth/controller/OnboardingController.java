package com.fancapital.backend.auth.controller;

import com.fancapital.backend.auth.service.WalletProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mod AST 5 - Logique Freemium et Onboarding.
 * Le paiement des frais d'entrée est la condition technique Trigger_Wallet_Creation
 * pour passer de "Observateur" à Tier Bronze.
 *
 * En production, cet endpoint serait appelé par le webhook du prestataire de paiement
 * après confirmation du paiement des frais d'entrée.
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

  private final WalletProvisioningService walletProvisioning;

  public OnboardingController(WalletProvisioningService walletProvisioning) {
    this.walletProvisioning = walletProvisioning;
  }

  /**
   * Déclenche la création du wallet après paiement des frais d'entrée.
   * Observateur → Tier Bronze (wallet créé, KYC1 requis pour activités).
   *
   * @param userId   ID utilisateur (ex: après inscription)
   * @param paymentRef Référence paiement (optionnel, pour audit)
   */
  @PostMapping("/trigger-wallet-creation")
  public ResponseEntity<Map<String, Object>> triggerWalletCreation(
      @RequestParam String userId,
      @RequestParam(required = false) String paymentRef
  ) {
    String wallet = walletProvisioning.ensureProvisioned(userId);
    return ResponseEntity.ok(Map.of(
        "userId", userId,
        "walletAddress", wallet,
        "paymentRef", paymentRef != null ? paymentRef : "",
        "status", "wallet_created"
    ));
  }
}
