package com.fancapital.backend.backoffice.audit.repo;

import com.fancapital.backend.backoffice.audit.model.AuditUserTokenBalance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditUserTokenBalanceRepository extends JpaRepository<AuditUserTokenBalance, String> {
  List<AuditUserTokenBalance> findByTokenAddressIgnoreCase(String tokenAddress);
}

