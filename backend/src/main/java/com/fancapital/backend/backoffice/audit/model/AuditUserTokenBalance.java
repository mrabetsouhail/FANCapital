package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_user_token_balances")
public class AuditUserTokenBalance {
  @Id
  @Column(nullable = false, updatable = false, length = 160)
  private String id; // userId|tokenAddress

  @Column(nullable = false, length = 60)
  private String userId;

  @Column(nullable = false, length = 42)
  private String walletAddress;

  @Column(nullable = false, length = 42)
  private String tokenAddress;

  @Column(nullable = false, length = 80)
  private String balance1e8 = "0"; // keep as decimal string (uint256 compatible)

  @Column(nullable = false)
  private long lastUpdatedBlock = 0L;

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  public static String key(String userId, String tokenAddress) {
    return userId + "|" + tokenAddress.toLowerCase();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getBalance1e8() {
    return balance1e8;
  }

  public void setBalance1e8(String balance1e8) {
    this.balance1e8 = balance1e8;
  }

  public long getLastUpdatedBlock() {
    return lastUpdatedBlock;
  }

  public void setLastUpdatedBlock(long lastUpdatedBlock) {
    this.lastUpdatedBlock = lastUpdatedBlock;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}

