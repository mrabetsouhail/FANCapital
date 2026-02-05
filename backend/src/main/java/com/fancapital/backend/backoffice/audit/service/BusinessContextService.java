package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.backoffice.audit.model.BusinessContextMapping;
import com.fancapital.backend.backoffice.audit.repo.BusinessContextMappingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des BusinessContextId pour la traçabilité des transactions.
 * 
 * Conforme au Livre Blanc FAN-Capital v2.1 - Section 4.2
 * Permet de remonter directement de l'adresse blockchain jusqu'à la pièce comptable
 * archivée dans la base de données MariaDB de la plateforme.
 */
@Service
public class BusinessContextService {
  private final BusinessContextMappingRepository repo;
  private final AuditLogService auditLog;

  public BusinessContextService(
      BusinessContextMappingRepository repo,
      AuditLogService auditLog
  ) {
    this.repo = repo;
    this.auditLog = auditLog;
  }

  /**
   * Enregistre le mapping entre une transaction blockchain et un BusinessContextId.
   * 
   * @param transactionHash Hash de la transaction blockchain
   * @param businessContextId Identifiant métier permettant de remonter à la pièce comptable
   * @param contractAddress Adresse du contrat concerné (optionnel)
   * @param operationType Type d'opération (MINT, BURN, TRANSFER, etc.)
   * @param description Description de l'opération
   * @param accountingDocumentId ID du document comptable associé (optionnel)
   * @return Le mapping créé
   */
  @Transactional
  public BusinessContextMapping registerTransaction(
      String transactionHash,
      String businessContextId,
      String contractAddress,
      String operationType,
      String description,
      String accountingDocumentId
  ) {
    if (transactionHash == null || transactionHash.isBlank()) {
      throw new IllegalArgumentException("transactionHash cannot be null or blank");
    }
    if (businessContextId == null || businessContextId.isBlank()) {
      throw new IllegalArgumentException("businessContextId cannot be null or blank");
    }

    // Vérifier si le mapping existe déjà
    Optional<BusinessContextMapping> existing = repo.findByTransactionHash(transactionHash);
    if (existing.isPresent()) {
      throw new IllegalStateException("Transaction " + transactionHash + " already registered");
    }

    BusinessContextMapping mapping = new BusinessContextMapping();
    mapping.setCreatedAt(Instant.now());
    mapping.setTransactionHash(transactionHash.toLowerCase());
    mapping.setBusinessContextId(businessContextId);
    mapping.setContractAddress(contractAddress);
    mapping.setOperationType(operationType);
    mapping.setDescription(description);
    mapping.setAccountingDocumentId(accountingDocumentId);

    BusinessContextMapping saved = repo.save(mapping);

    auditLog.append(
        "BUSINESS_CONTEXT_REGISTERED",
        null,
        null,
        null,
        null,
        null,
        String.format("txHash=%s,businessContextId=%s,operation=%s", transactionHash, businessContextId, operationType)
    );

    return saved;
  }

  /**
   * Récupère le BusinessContextId associé à une transaction.
   */
  public Optional<BusinessContextMapping> findByTransactionHash(String transactionHash) {
    return repo.findByTransactionHash(transactionHash.toLowerCase());
  }

  /**
   * Récupère toutes les transactions associées à un BusinessContextId.
   */
  public Optional<BusinessContextMapping> findByBusinessContextId(String businessContextId) {
    return repo.findByBusinessContextId(businessContextId);
  }

  /**
   * Génère un BusinessContextId unique basé sur un préfixe et un UUID.
   */
  public String generateBusinessContextId(String prefix) {
    return String.format("%s-%s", prefix, UUID.randomUUID().toString().substring(0, 8));
  }
}
