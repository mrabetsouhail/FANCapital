package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.config.BackofficeProperties;
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

    this.adminEmails = normalize(props.tax() != null ? props.tax().adminEmails() : List.of());
    this.regulatorEmails = normalize(props.audit() != null ? props.audit().regulatorEmails() : List.of());
    this.complianceEmails = normalize(props.audit() != null ? props.audit().complianceEmails() : List.of());
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
    if (!adminEmails.contains(email)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
  }

  /** Read-only access to audit registry (Regulator/Compliance/Admin). */
  public void requireAuditRead() {
    BackofficeRole r = currentBackofficeRole();
    if (r == BackofficeRole.NONE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
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
}

