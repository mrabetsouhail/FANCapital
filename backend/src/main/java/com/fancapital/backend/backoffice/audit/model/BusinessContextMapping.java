package com.fancapital.backend.backoffice.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapping entre une transaction blockchain et un BusinessContextId.
 * 
 * Conforme au Livre Blanc FAN-Capital v2.1 - Section 4.2
 * Permet de remonter directement de l'adresse blockchain jusqu'à la pièce comptable
 * archivée dans la base de données MariaDB de la plateforme.
 */
@Entity
@Table(name = "business_context_mappings")
public class BusinessContextMapping {
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false, length = 66, unique = true)
  private String transactionHash;

  @Column(nullable = false, length = 80)
  private String businessContextId;

  @Column(length = 42)
  private String contractAddress;

  @Column(length = 200)
  private String operationType; // MINT, BURN, TRANSFER, etc.

  @Column(length = 500)
  private String description;

  @Column(length = 80)
  private String accountingDocumentId;

  public BusinessContextMapping() {}

  public String getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getTransactionHash() {
    return transactionHash;
  }

  public void setTransactionHash(String transactionHash) {
    this.transactionHash = transactionHash;
  }

  public String getBusinessContextId() {
    return businessContextId;
  }

  public void setBusinessContextId(String businessContextId) {
    this.businessContextId = businessContextId;
  }

  public String getContractAddress() {
    return contractAddress;
  }

  public void setContractAddress(String contractAddress) {
    this.contractAddress = contractAddress;
  }

  public String getOperationType() {
    return operationType;
  }

  public void setOperationType(String operationType) {
    this.operationType = operationType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAccountingDocumentId() {
    return accountingDocumentId;
  }

  public void setAccountingDocumentId(String accountingDocumentId) {
    this.accountingDocumentId = accountingDocumentId;
  }
}
