package com.fancapital.backend.blockchain.repo;

import com.fancapital.backend.blockchain.model.AdvanceInterestTracking;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvanceInterestTrackingRepository extends JpaRepository<AdvanceInterestTracking, String> {

  Optional<AdvanceInterestTracking> findByLoanId(String loanId);
}
