package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_token_sync_state")
public class AuditTokenSyncState {
  @Id
  @Column(nullable = false, updatable = false, length = 42)
  private String tokenAddress;

  @Column(nullable = false)
  private long lastProcessedBlock = 0L;

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  public AuditTokenSyncState() {}

  public AuditTokenSyncState(String tokenAddress) {
    this.tokenAddress = tokenAddress;
  }

  public String getTokenAddress() {
    return tokenAddress;
  }

  public void setTokenAddress(String tokenAddress) {
    this.tokenAddress = tokenAddress;
  }

  public long getLastProcessedBlock() {
    return lastProcessedBlock;
  }

  public void setLastProcessedBlock(long lastProcessedBlock) {
    this.lastProcessedBlock = lastProcessedBlock;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}

