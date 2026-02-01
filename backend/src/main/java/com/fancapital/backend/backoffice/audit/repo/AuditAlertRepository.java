package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.AuditAlert;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditAlertRepository extends JpaRepository<AuditAlert, String> {
  @Query("select a from AuditAlert a where a.resolvedAt is null order by a.createdAt desc")
  List<AuditAlert> findOpen(Pageable pageable);

  @Query("select a from AuditAlert a order by a.createdAt desc")
  List<AuditAlert> findLatest(Pageable pageable);

  AuditAlert findTop1ByResolvedAtIsNullAndUserIdAndTokenAddressIgnoreCaseOrderByCreatedAtDesc(String userId, String tokenAddress);
}

