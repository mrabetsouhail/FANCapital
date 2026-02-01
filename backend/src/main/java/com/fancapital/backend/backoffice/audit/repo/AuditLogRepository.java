package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.AuditLogEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {
  @Query("select a from AuditLogEntry a order by a.createdAt desc")
  List<AuditLogEntry> findLatest(Pageable pageable);

  @Query("select a from AuditLogEntry a order by a.createdAt desc")
  List<AuditLogEntry> findAllDesc();
}

