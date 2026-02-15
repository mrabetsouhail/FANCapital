package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.audit.model.AuditDtos;
import com.fancapital.backend.backoffice.audit.service.AuditRegistryService;
import com.fancapital.backend.backoffice.model.EscrowBackofficeDtos;
import com.fancapital.backend.blockchain.service.CreditReadService;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.EscrowReadService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service backoffice pour Escrow & Libération au Prorata.
 * - Registre des actifs bloqués (tokens escrowLocked par utilisateur)
 * - Suivi des remboursements (calendrier, déblocage progressif)
 */
@Service
public class EscrowBackofficeService {

  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);
  private static final BigInteger LTV_BPS = BigInteger.valueOf(7_000);
  private static final BigInteger BPS = BigInteger.valueOf(10_000);

  private final AuditRegistryService auditRegistry;
  private final CreditReadService creditRead;
  private final EscrowReadService escrowRead;
  private final AppUserRepository userRepo;
  private final DeploymentRegistry registry;

  public EscrowBackofficeService(AuditRegistryService auditRegistry, CreditReadService creditRead,
      EscrowReadService escrowRead, AppUserRepository userRepo, DeploymentRegistry registry) {
    this.auditRegistry = auditRegistry;
    this.creditRead = creditRead;
    this.escrowRead = escrowRead;
    this.userRepo = userRepo;
    this.registry = registry;
  }

  /**
   * Registre des actifs bloqués : utilisateurs ayant des tokens en séquestre (Atlas/Didon).
   */
  public EscrowBackofficeDtos.LockedAssetsResponse listLockedAssets() {
    AuditDtos.AuditRegistryResponse audit = auditRegistry.registry("", null);
    List<EscrowBackofficeDtos.LockedAssetRow> rows = new ArrayList<>();
    for (AuditDtos.AuditRegistryRow r : audit.rows()) {
      BigInteger atlasLocked = parseBigInt(r.atlasLocked1e8());
      BigInteger didonLocked = parseBigInt(r.didonLocked1e8());
      if (atlasLocked.compareTo(BigInteger.ZERO) <= 0 && didonLocked.compareTo(BigInteger.ZERO) <= 0) {
        continue;
      }
      rows.add(new EscrowBackofficeDtos.LockedAssetRow(
          r.userId(),
          r.email(),
          r.fullNameOrCompany(),
          r.walletAddress(),
          r.atlasLocked1e8(),
          r.didonLocked1e8(),
          r.atlasBalanceToken1e8(),
          r.didonBalanceToken1e8()
      ));
    }
    return new EscrowBackofficeDtos.LockedAssetsResponse(rows, rows.size());
  }

  /**
   * Suivi des remboursements : prêts actifs avec avancement et déblocage progressif.
   */
  public EscrowBackofficeDtos.RepaymentTrackingResponse listRepaymentTracking() {
    List<CreditReadService.LoanInfo> activeLoans = creditRead.listActiveLoans();
    List<EscrowBackofficeDtos.RepaymentTrackingRow> rows = new ArrayList<>();

    for (CreditReadService.LoanInfo loan : activeLoans) {
      EscrowReadService.LockInfo lock = escrowRead.getLock(loan.loanId());
      if (lock == null || !lock.active()) continue;

      BigInteger originalCollateral = loan.collateralAmount();
      BigInteger currentLocked = lock.amount();
      BigInteger remainingDebt = loan.principalTnd();
      BigInteger vniAtStart = loan.vniAtStart();

      // originalPrincipal = collateralAmount * vniAtStart * LTV / (BPS * PRICE_SCALE)
      BigInteger originalPrincipal = originalCollateral.multiply(vniAtStart).multiply(LTV_BPS)
          .divide(BPS).divide(PRICE_SCALE);
      if (originalPrincipal.signum() <= 0) originalPrincipal = BigInteger.ONE;

      BigInteger repaid = originalPrincipal.subtract(remainingDebt);
      String repaidPercent = originalPrincipal.signum() > 0
          ? repaid.multiply(BigInteger.valueOf(100)).divide(originalPrincipal).toString()
          : "0";
      BigInteger released = originalCollateral.subtract(currentLocked);
      String releasedPercent = originalCollateral.signum() > 0
          ? released.multiply(BigInteger.valueOf(100)).divide(originalCollateral).toString()
          : "0";

      String tokenSymbol = registry.findByToken(loan.token()).map(f -> f.symbol() != null ? f.symbol() : f.name()).orElse("CPEF");
      String scheduleLabel = formatDuration(loan.durationDays());

      Optional<AppUser> userOpt = userRepo.findByWalletAddressIgnoreCase(loan.user());
      String email = userOpt.map(AppUser::getEmail).orElse("—");
      String fullName = userOpt.map(u -> u.getNom() != null && u.getPrenom() != null
          ? u.getPrenom() + " " + u.getNom()
          : u.getDenominationSociale() != null ? u.getDenominationSociale() : u.getEmail()).orElse("—");

      rows.add(new EscrowBackofficeDtos.RepaymentTrackingRow(
          loan.loanId().toString(),
          loan.user(),
          email,
          fullName,
          tokenSymbol,
          originalCollateral.toString(),
          currentLocked.toString(),
          originalPrincipal.toString(),
          remainingDebt.toString(),
          repaidPercent,
          releasedPercent,
          loan.durationDays(),
          loan.startAt(),
          scheduleLabel,
          loan.status()
      ));
    }

    return new EscrowBackofficeDtos.RepaymentTrackingResponse(rows, rows.size());
  }

  private static BigInteger parseBigInt(String s) {
    if (s == null || s.isBlank()) return BigInteger.ZERO;
    try {
      return new BigInteger(s.trim());
    } catch (NumberFormatException e) {
      return BigInteger.ZERO;
    }
  }

  private static String formatDuration(long days) {
    if (days <= 90) return "3 mois";
    if (days <= 120) return "4 mois";
    if (days <= 150) return "5 mois";
    if (days <= 365) return "12 mois";
    return days + " jours";
  }
}
