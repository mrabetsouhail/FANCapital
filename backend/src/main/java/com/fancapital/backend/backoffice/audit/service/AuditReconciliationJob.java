package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditReconciliationJob {
  private final BackofficeProperties props;
  private final AuditReconciliationService recon;

  public AuditReconciliationJob(BackofficeProperties props, AuditReconciliationService recon) {
    this.props = props;
    this.recon = recon;
  }

  @Scheduled(fixedDelayString = "${backoffice.audit.reconciliation-interval-ms:300000}")
  public void run() {
    if (props.audit() == null || !props.audit().reconciliationEnabled()) return;
    // actor is null for background job; it will still create alerts + audit log
    recon.reconcileOnce(null, null);
  }
}

