package com.fancapital.backend.blockchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job quotidien pour lister les snapshots AUM (Spécifications Financières v4.7 - Inertie AUM).
 */
@Component
public class AumSnapshotJob {

  private static final Logger log = LoggerFactory.getLogger(AumSnapshotJob.class);

  private final AumSnapshotService aumSnapshotService;

  public AumSnapshotJob(AumSnapshotService aumSnapshotService) {
    this.aumSnapshotService = aumSnapshotService;
  }

  @Scheduled(cron = "${blockchain.aum.snapshot-cron:0 0 1 * * ?}")
  public void runSnapshot() {
    log.info("Starting AUM snapshot job");
    try {
      aumSnapshotService.snapshotAllUsers();
      log.info("AUM snapshot job completed");
    } catch (Exception e) {
      log.error("AUM snapshot job failed", e);
    }
  }
}
