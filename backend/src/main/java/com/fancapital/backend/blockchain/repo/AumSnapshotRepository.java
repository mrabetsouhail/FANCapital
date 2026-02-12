package com.fancapital.backend.blockchain.repo;

import com.fancapital.backend.blockchain.model.AumSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AumSnapshotRepository extends JpaRepository<AumSnapshot, String> {

  List<AumSnapshot> findByWalletAddressIgnoreCaseAndSnapshotDateBetween(
      String walletAddress, LocalDate start, LocalDate end);
}
