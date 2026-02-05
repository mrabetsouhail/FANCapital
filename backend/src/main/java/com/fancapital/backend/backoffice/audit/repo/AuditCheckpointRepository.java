package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.AuditCheckpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditCheckpointRepository extends JpaRepository<AuditCheckpoint, String> {
  
  Optional<AuditCheckpoint> findTopByTokenAddressOrderByBlockNumberDesc(String tokenAddress);
  
  Optional<AuditCheckpoint> findTopByOrderByBlockNumberDesc();
  
  List<AuditCheckpoint> findByTokenAddressAndBlockNumberLessThanEqualOrderByBlockNumberDesc(
      String tokenAddress, long blockNumber);
  
  @Query("SELECT c FROM AuditCheckpoint c WHERE c.blockNumber <= ?1 ORDER BY c.blockNumber DESC")
  List<AuditCheckpoint> findLatestCheckpointBeforeBlock(long blockNumber);
}
