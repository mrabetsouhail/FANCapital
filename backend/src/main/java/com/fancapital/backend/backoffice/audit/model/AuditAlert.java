package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_alerts")
public class AuditAlert {
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false, length = 30)
  private String severity; // CRITICAL | HIGH | MEDIUM

  @Column(nullable = false, length = 60)
  private String kind; // BALANCE_MISMATCH

  @Column(length = 60)
  private String userId;

  @Column(length = 42)
  private String walletAddress;

  @Column(length = 42)
  private String tokenAddress;

  @Column(length = 80)
  private String expectedBalance1e8;

  @Column(length = 80)
  private String onchainBalance1e8;

  @Column(nullable = false)
  private long checkedAtBlock = 0L;

  @Column(length = 200)
  private String details;

  @Column
  private Instant resolvedAt;

  @Column(length = 60)
  private String resolvedByUserId;

  public String getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getWalletAddress() {
    return walletAddress;
  }

  public void setWalletAddress(String walletAddress) {
    this.walletAddress = walletAddress;
  }

  public String getTokenAddress() {
    return tokenAddress;
  }

  public void setTokenAddress(String tokenAddress) {
    this.tokenAddress = tokenAddress;
  }

  public String getExpectedBalance1e8() {
    return expectedBalance1e8;
  }

  public void setExpectedBalance1e8(String expectedBalance1e8) {
    this.expectedBalance1e8 = expectedBalance1e8;
  }

  public String getOnchainBalance1e8() {
    return onchainBalance1e8;
  }

  public void setOnchainBalance1e8(String onchainBalance1e8) {
    this.onchainBalance1e8 = onchainBalance1e8;
  }

  public long getCheckedAtBlock() {
    return checkedAtBlock;
  }

  public void setCheckedAtBlock(long checkedAtBlock) {
    this.checkedAtBlock = checkedAtBlock;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public String getResolvedByUserId() {
    return resolvedByUserId;
  }

  public void setResolvedByUserId(String resolvedByUserId) {
    this.resolvedByUserId = resolvedByUserId;
  }
}

