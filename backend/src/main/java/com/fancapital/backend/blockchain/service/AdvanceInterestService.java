package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.repo.AdvanceInterestTrackingRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Expose les infos d'intérêts fixes (totalInterest, interestPaid) pour l'API advance/active. */
@Service
public class AdvanceInterestService {

  private final AdvanceInterestTrackingRepository interestTrackingRepo;

  public AdvanceInterestService(AdvanceInterestTrackingRepository interestTrackingRepo) {
    this.interestTrackingRepo = interestTrackingRepo;
  }

  /** Ajoute totalInterestTnd et interestPaidTnd au body si un suivi existe. */
  public void addInterestInfo(String loanId, Map<String, Object> body) {
    interestTrackingRepo.findByLoanId(loanId).ifPresent(t -> {
      body.put("totalInterestTnd", t.getTotalInterestTnd1e8());
      body.put("interestPaidTnd", t.getInterestPaidTnd1e8());
    });
  }
}
