package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.config.BackofficeProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackofficeAuthzService {
  private final AppUserRepository repo;
  private final Set<String> adminEmails;
  private final Set<String> regulatorEmails;
  private final Set<String> complianceEmails;

  public BackofficeAuthzService(AppUserRepository repo, BackofficeProperties props) {
    this.repo = repo;

    this.adminEmails = normalize(parseCommaSeparated(props.tax() != null ? props.tax().adminEmails() : List.of()));
    this.regulatorEmails = normalize(parseCommaSeparated(props.audit() != null ? props.audit().regulatorEmails() : List.of()));
    this.complianceEmails = normalize(parseCommaSeparated(props.audit() != null ? props.audit().complianceEmails() : List.of()));
  }

  public enum BackofficeRole {
    NONE,
    ADMIN,
    COMPLIANCE,
    REGULATOR
  }

  public void requireAdmin() {
    if (adminEmails.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_EMAILS not configured for backoffice actions.");
    }
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getPrincipal() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    String userId = String.valueOf(a.getPrincipal());
    String email = repo.findById(userId).map(u -> u.getEmail().toLowerCase()).orElse("");
    if (email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: User email not found for userId=" + userId);
    }
    if (!adminEmails.contains(email)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
          String.format("Forbidden: Email '%s' is not in admin list. Configured admins: %s", email, adminEmails));
    }
  }

  /** Read-only access to audit registry (Regulator/Compliance/Admin). */
  public void requireAuditRead() {
    BackofficeRole r = currentBackofficeRole();
    if (r == BackofficeRole.NONE) {
      Authentication a = SecurityContextHolder.getContext().getAuthentication();
      String userId = a != null && a.getPrincipal() != null ? String.valueOf(a.getPrincipal()) : "unknown";
      String email = repo.findById(userId).map(u -> u.getEmail().toLowerCase()).orElse("");
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
          String.format("Forbidden: Email '%s' has no audit access. Required: ADMIN_EMAILS, AUDIT_REGULATOR_EMAILS, or AUDIT_COMPLIANCE_EMAILS. " +
              "Configured admins: %s, regulators: %s, compliance: %s", 
              email, adminEmails, regulatorEmails, complianceEmails));
    }
  }

  /** Export/logs access for Compliance/Admin (Regulator is read-only). */
  public void requireAuditExport() {
    BackofficeRole r = currentBackofficeRole();
    if (r != BackofficeRole.ADMIN && r != BackofficeRole.COMPLIANCE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
  }

  public BackofficeRole currentBackofficeRole() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getPrincipal() == null) return BackofficeRole.NONE;
    String userId = String.valueOf(a.getPrincipal());
    String email = repo.findById(userId).map(u -> u.getEmail().toLowerCase()).orElse("");
    if (email.isBlank()) return BackofficeRole.NONE;
    if (adminEmails.contains(email)) return BackofficeRole.ADMIN;
    if (complianceEmails.contains(email)) return BackofficeRole.COMPLIANCE;
    if (regulatorEmails.contains(email)) return BackofficeRole.REGULATOR;
    return BackofficeRole.NONE;
  }

  @Nullable
  public String currentUserId() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getPrincipal() == null) return null;
    String userId = String.valueOf(a.getPrincipal());
    if (userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)) return null;
    return userId;
  }

  @Nullable
  public String currentUserEmail() {
    String userId = currentUserId();
    if (userId == null) return null;
    return repo.findById(userId).map(u -> u.getEmail()).orElse(null);
  }

  public boolean isAdminEmail(String email) {
    if (email == null) return false;
    return adminEmails.contains(email.trim().toLowerCase());
  }

  public BackofficeRole roleForEmail(String email) {
    if (email == null) return BackofficeRole.NONE;
    String e = email.trim().toLowerCase();
    if (e.isBlank()) return BackofficeRole.NONE;
    if (adminEmails.contains(e)) return BackofficeRole.ADMIN;
    if (complianceEmails.contains(e)) return BackofficeRole.COMPLIANCE;
    if (regulatorEmails.contains(e)) return BackofficeRole.REGULATOR;
    return BackofficeRole.NONE;
  }

  /**
   * Parse comma-separated string into list.
   * Spring Boot doesn't automatically parse comma-separated env vars into lists,
   * so if ADMIN_EMAILS="a@b.com,c@d.com", it comes as a single-element list.
   */
  private static List<String> parseCommaSeparated(List<String> input) {
    if (input == null || input.isEmpty()) return List.of();
    List<String> result = new ArrayList<>();
    for (String item : input) {
      if (item == null) continue;
      // If the item contains commas, split it
      if (item.contains(",")) {
        String[] parts = item.split(",");
        for (String part : parts) {
          String trimmed = part.trim();
          if (!trimmed.isBlank()) result.add(trimmed);
        }
      } else {
        String trimmed = item.trim();
        if (!trimmed.isBlank()) result.add(trimmed);
      }
    }
    return result;
  }

  private static Set<String> normalize(List<String> configured) {
    Set<String> s = new HashSet<>();
    if (configured == null) return s;
    for (String e : configured) {
      if (e == null) continue;
      String x = e.trim().toLowerCase();
      if (!x.isBlank()) s.add(x);
    }
    return s;
  }

  /** Debug helper: returns configured admin emails (for troubleshooting). */
  public Set<String> getAdminEmailsForDebug() {
    return new HashSet<>(adminEmails);
  }
}

