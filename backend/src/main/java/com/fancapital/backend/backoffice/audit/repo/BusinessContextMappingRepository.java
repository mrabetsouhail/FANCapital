package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.BusinessContextMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessContextMappingRepository extends JpaRepository<BusinessContextMapping, String> {
  
  Optional<BusinessContextMapping> findByTransactionHash(String transactionHash);
  
  Optional<BusinessContextMapping> findByBusinessContextId(String businessContextId);
}
