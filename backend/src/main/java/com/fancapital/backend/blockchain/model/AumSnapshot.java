package com.fancapital.backend.blockchain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Snapshot quotidien de l'AUM (Actifs sous gestion) par wallet.
 * Utilisé pour le calcul AUM_90j dans la formule SCI (Spécifications Financières v4.7).
 */
@Entity
@Table(name = "aum_snapshots")
public class AumSnapshot {

  /** Clé composite : walletAddress|yyyy-MM-dd */
  @Id
  @Column(nullable = false, updatable = false, length = 60)
  private String id;

  @Column(nullable = false, length = 42)
  private String walletAddress;

  @Column(nullable = false)
  private LocalDate snapshotDate;

  /** Total valorisation TND (échelle 1e8) */
  @Column(nullable = false, length = 40)
  private String totalValueTnd1e8 = "0";

  public static String buildId(String walletAddress, LocalDate date) {
    return (walletAddress != null ? walletAddress.toLowerCase() : "") + "|" + date;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWalletAddress() {
    return walletAddress;
  }

  public void setWalletAddress(String walletAddress) {
    this.walletAddress = walletAddress;
  }

  public LocalDate getSnapshotDate() {
    return snapshotDate;
  }

  public void setSnapshotDate(LocalDate snapshotDate) {
    this.snapshotDate = snapshotDate;
  }

  public String getTotalValueTnd1e8() {
    return totalValueTnd1e8;
  }

  public void setTotalValueTnd1e8(String totalValueTnd1e8) {
    this.totalValueTnd1e8 = totalValueTnd1e8;
  }
}
