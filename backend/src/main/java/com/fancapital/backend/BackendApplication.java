package com.fancapital.backend;

import com.fancapital.backend.backoffice.config.BackofficeProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
  private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }

  @Bean
  CommandLineRunner backofficeConfigLog(BackofficeProperties props) {
    return args -> {
      List<String> raw = props.tax() != null ? props.tax().adminEmails() : null;
      List<String> normalized = new ArrayList<>();
      if (raw != null) {
        for (String e : raw) {
          if (e == null) continue;
          String x = e.trim().toLowerCase();
          if (!x.isBlank()) normalized.add(x);
        }
      }
      if (normalized.isEmpty()) {
        log.warn("Backoffice admin emails not configured (env ADMIN_EMAILS). UI will not show backoffice menu and /api/backoffice/** will be unavailable.");
      } else {
        log.info("Backoffice admin emails configured: {}", normalized);
      }

      List<String> reg = props.audit() != null ? props.audit().regulatorEmails() : null;
      List<String> comp = props.audit() != null ? props.audit().complianceEmails() : null;
      int regCount = reg == null ? 0 : (int) reg.stream().filter(x -> x != null && !x.trim().isBlank()).count();
      int compCount = comp == null ? 0 : (int) comp.stream().filter(x -> x != null && !x.trim().isBlank()).count();
      if (regCount == 0 && compCount == 0) {
        log.warn("Audit backoffice roles not configured (AUDIT_REGULATOR_EMAILS / AUDIT_COMPLIANCE_EMAILS). Audit registry endpoints will be forbidden for non-admin.");
      } else {
        log.info("Audit backoffice roles configured: regulatorEmails={}, complianceEmails={}", regCount, compCount);
      }
    };
  }
}

