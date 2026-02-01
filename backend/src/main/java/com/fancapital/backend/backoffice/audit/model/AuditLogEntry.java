package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable-ish audit log (append-only).
 *
 * We chain entries by storing previousHash + computing entryHash (SHA-256),
 * so tampering becomes detectable (similar to a hash-chain).
 */
@Entity
@Table(name = "audit_log_entries")
public class AuditLogEntry {
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false, length = 60)
  private String action;

  @Column(length = 60)
  private String actorUserId;

  @Column(length = 200)
  private String actorEmail;

  @Column(length = 60)
  private String targetUserId;

  @Column(length = 80)
  private String ip;

  @Column(length = 300)
  private String userAgent;

  @Column(length = 80)
  private String previousHash;

  @Column(nullable = false, length = 80)
  private String entryHash;

  @Column(length = 120)
  private String details;

  public String getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getActorEmail() {
    return actorEmail;
  }

  public void setActorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
  }

  public String getTargetUserId() {
    return targetUserId;
  }

  public void setTargetUserId(String targetUserId) {
    this.targetUserId = targetUserId;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getPreviousHash() {
    return previousHash;
  }

  public void setPreviousHash(String previousHash) {
    this.previousHash = previousHash;
  }

  public String getEntryHash() {
    return entryHash;
  }

  public void setEntryHash(String entryHash) {
    this.entryHash = entryHash;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}

