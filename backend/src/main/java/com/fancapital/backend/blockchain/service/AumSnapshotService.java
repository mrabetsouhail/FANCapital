package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.model.AumSnapshot;
import com.fancapital.backend.blockchain.repo.AumSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service AUM 90 jours (Spécifications Financières v4.7 - Inertie AUM).
 * Calcule la moyenne des actifs sous gestion sur 90 jours pour éviter les sauts de niveau opportunistes.
 */
@Service
public class AumSnapshotService {

  private static final Logger log = LoggerFactory.getLogger(AumSnapshotService.class);
  private static final int AUM_WINDOW_DAYS = 90;

  private final AumSnapshotRepository aumRepo;
  private final BlockchainReadService blockchainRead;
  private final AppUserRepository userRepo;

  public AumSnapshotService(AumSnapshotRepository aumRepo, BlockchainReadService blockchainRead,
      AppUserRepository userRepo) {
    this.aumRepo = aumRepo;
    this.blockchainRead = blockchainRead;
    this.userRepo = userRepo;
  }

  /**
   * Moyenne AUM sur les 90 derniers jours pour un wallet.
   *
   * @param walletAddress adresse du wallet
   * @return moyenne TND (échelle 1e8), ou null si pas assez de données
   */
  public BigDecimal getAum90DaysAvgTnd(String walletAddress) {
    if (walletAddress == null || walletAddress.isBlank()) return null;
    LocalDate end = LocalDate.now();
    LocalDate start = end.minusDays(AUM_WINDOW_DAYS);
    List<AumSnapshot> snapshots = aumRepo.findByWalletAddressIgnoreCaseAndSnapshotDateBetween(
        walletAddress.trim(), start, end);
    if (snapshots.isEmpty()) return null;
    BigDecimal sum = BigDecimal.ZERO;
    for (AumSnapshot s : snapshots) {
      try {
        sum = sum.add(new BigDecimal(s.getTotalValueTnd1e8()));
      } catch (NumberFormatException e) {
        log.warn("Invalid totalValueTnd1e8 in snapshot {}: {}", s.getId(), e.getMessage());
      }
    }
    return sum.divide(BigDecimal.valueOf(snapshots.size()), 8, RoundingMode.HALF_UP);
  }

  /**
   * Exécute un snapshot pour tous les utilisateurs ayant un wallet.
   * À appeler quotidiennement (cron).
   */
  public void snapshotAllUsers() {
    LocalDate today = LocalDate.now();
    userRepo.findAll().stream()
        .filter(u -> u.getWalletAddress() != null && !u.getWalletAddress().isBlank())
        .forEach(u -> snapshotForWallet(u.getWalletAddress(), today));
  }

  /**
   * Snapshot AUM pour un wallet à une date donnée.
   */
  public void snapshotForWallet(String walletAddress, LocalDate date) {
    if (walletAddress == null || walletAddress.isBlank()) return;
    try {
      var port = blockchainRead.portfolio(walletAddress);
      BigDecimal total = new BigDecimal(port.totalValueTnd()).add(new BigDecimal(port.cashBalanceTnd()));
      String totalStr = total.toBigInteger().toString();

      AumSnapshot s = aumRepo.findById(AumSnapshot.buildId(walletAddress, date)).orElse(new AumSnapshot());
      s.setId(AumSnapshot.buildId(walletAddress, date));
      s.setWalletAddress(walletAddress.trim().toLowerCase());
      s.setSnapshotDate(date);
      s.setTotalValueTnd1e8(totalStr != null ? totalStr : "0");
      aumRepo.save(s);
    } catch (Exception e) {
      log.warn("AUM snapshot failed for {} at {}: {}", walletAddress, date, e.getMessage());
    }
  }
}
