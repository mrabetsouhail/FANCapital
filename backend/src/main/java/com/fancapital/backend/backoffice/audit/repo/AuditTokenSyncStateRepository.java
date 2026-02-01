package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.AuditTokenSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditTokenSyncStateRepository extends JpaRepository<AuditTokenSyncState, String> {}

