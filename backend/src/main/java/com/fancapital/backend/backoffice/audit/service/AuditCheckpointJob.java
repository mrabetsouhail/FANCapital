package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import java.net.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job planifié pour générer automatiquement les checkpoints d'audit tous les 10 000 blocs.
 *
 * Conforme au Livre Blanc FAN-Capital v2.1 - Section 4.1
 * Si le nœud blockchain (ex. 127.0.0.1:8545) est indisponible, le job log un message
 * et passe son tour sans faire échouer l'application.
 */
@Component
public class AuditCheckpointJob {
  private static final Logger log = LoggerFactory.getLogger(AuditCheckpointJob.class);

  private final BackofficeProperties props;
  private final AuditProofService auditProof;

  public AuditCheckpointJob(BackofficeProperties props, AuditProofService auditProof) {
    this.props = props;
    this.auditProof = auditProof;
  }

  @Scheduled(fixedDelayString = "${backoffice.audit.checkpoint-interval-ms:600000}") // 10 minutes par défaut
  public void generateCheckpoints() {
    if (props.audit() == null || !props.audit().checkpointEnabled()) {
      return;
    }
    try {
      auditProof.generateCheckpointsForAllTokens();
    } catch (Exception e) {
      if (isConnectionRefused(e)) {
        log.warn("Audit checkpoints skipped: blockchain node unreachable (e.g. 127.0.0.1:8545). Start the node to enable audit proof.");
      } else {
        log.error("Failed to generate audit checkpoints: {}", e.getMessage(), e);
      }
    }
  }

  private static boolean isConnectionRefused(Exception e) {
    Throwable t = e;
    while (t != null) {
      if (t instanceof ConnectException) return true;
      String msg = t.getMessage();
      if (msg != null && (msg.contains("Failed to connect") || msg.contains("Connection refused"))) return true;
      t = t.getCause();
    }
    return false;
  }
}
