package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {
  private final AppUserRepository repo;
  private final WalletProvisioningService walletProvisioningService;
  private final com.fancapital.backend.blockchain.service.OnchainBootstrapService onchainBootstrapService;

  public KycService(
      AppUserRepository repo,
      WalletProvisioningService walletProvisioningService,
      com.fancapital.backend.blockchain.service.OnchainBootstrapService onchainBootstrapService
  ) {
    this.repo = repo;
    this.walletProvisioningService = walletProvisioningService;
    this.onchainBootstrapService = onchainBootstrapService;
  }

  /**
   * Admin/backoffice action: validate user's KYC to a target level (0..2).
   * When level becomes >=1, we auto-provision a WaaS wallet if none exists.
   */
  @Transactional
  public AppUser setKycLevel(String userId, int level) {
    if (level < 0 || level > 2) throw new IllegalArgumentException("Invalid KYC level");
    AppUser u = repo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    u.setKycLevel(level);
    u.setKycValidatedAt(level >= 1 ? Instant.now() : null);
    repo.save(u);

    if (level >= 1) {
      walletProvisioningService.ensureProvisionedForKycLevel1(userId);
      // Dev/MVP: bootstrap on-chain wallet state (whitelist + TND + approvals)
      onchainBootstrapService.bootstrapUser(userId);
    }
    return u;
  }
}

