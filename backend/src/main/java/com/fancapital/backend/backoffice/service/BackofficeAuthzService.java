package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.config.BackofficeProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BackofficeAuthzService {
  private final AppUserRepository repo;
  private final Set<String> adminEmails;

  public BackofficeAuthzService(AppUserRepository repo, BackofficeProperties props) {
    this.repo = repo;
    List<String> configured = props.tax() != null ? props.tax().adminEmails() : List.of();
    Set<String> s = new HashSet<>();
    if (configured != null) {
      for (String e : configured) {
        if (e == null) continue;
        String x = e.trim().toLowerCase();
        if (!x.isBlank()) s.add(x);
      }
    }
    this.adminEmails = s;
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

  public boolean isAdminEmail(String email) {
    if (email == null) return false;
    return adminEmails.contains(email.trim().toLowerCase());
  }
}

