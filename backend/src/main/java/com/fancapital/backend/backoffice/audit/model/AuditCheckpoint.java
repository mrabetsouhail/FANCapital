package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint d'audit généré tous les 10 000 blocs pour optimiser les inspections réglementaires.
 * 
 * Conforme au Livre Blanc FAN-Capital v2.1 - Section 4.1
 * La preuve mathématique est calculée de manière incrémentale à partir du dernier snapshot validé.
 */
@Entity
@Table(name = "audit_checkpoints")
public class AuditCheckpoint {
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private long blockNumber;

  @Column(nullable = false, length = 80)
  private String blockHash;

  @Column(length = 42)
  private String tokenAddress;

  @Column(nullable = false, length = 80)
  private String totalSupply1e8;

  @Column(nullable = false, length = 80)
  private String proofHash;

  @Column(length = 80)
  private String previousCheckpointHash;

  @Column(length = 500)
  private String metadata;

  public AuditCheckpoint() {}

  public String getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public void setBlockNumber(long blockNumber) {
    this.blockNumber = blockNumber;
  }

  public String getBlockHash() {
    return blockHash;
  }

  public void setBlockHash(String blockHash) {
    this.blockHash = blockHash;
  }

  public String getTokenAddress() {
    return tokenAddress;
  }

  public void setTokenAddress(String tokenAddress) {
    this.tokenAddress = tokenAddress;
  }

  public String getTotalSupply1e8() {
    return totalSupply1e8;
  }

  public void setTotalSupply1e8(String totalSupply1e8) {
    this.totalSupply1e8 = totalSupply1e8;
  }

  public String getProofHash() {
    return proofHash;
  }

  public void setProofHash(String proofHash) {
    this.proofHash = proofHash;
  }

  public String getPreviousCheckpointHash() {
    return previousCheckpointHash;
  }

  public void setPreviousCheckpointHash(String previousCheckpointHash) {
    this.previousCheckpointHash = previousCheckpointHash;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }
}
