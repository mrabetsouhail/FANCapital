package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.backoffice.audit.model.AuditLogEntry;
import com.fancapital.backend.backoffice.audit.repo.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
  private final AuditLogRepository repo;

  public AuditLogService(AuditLogRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public AuditLogEntry append(String action, String actorUserId, String actorEmail, String targetUserId, String ip, String userAgent, String details) {
    String prev = lastHash();
    AuditLogEntry e = new AuditLogEntry();
    e.setCreatedAt(Instant.now());
    e.setAction(action);
    e.setActorUserId(actorUserId);
    e.setActorEmail(actorEmail);
    e.setTargetUserId(targetUserId);
    e.setIp(ip);
    e.setUserAgent(userAgent);
    e.setPreviousHash(prev);
    e.setDetails(details);
    e.setEntryHash(hashEntry(prev, e));
    return repo.save(e);
  }

  public List<AuditLogEntry> latest(int limit) {
    int l = Math.max(1, Math.min(500, limit));
    return repo.findLatest(PageRequest.of(0, l));
  }

  private String lastHash() {
    List<AuditLogEntry> last = repo.findLatest(PageRequest.of(0, 1));
    if (last.isEmpty()) return "0x0";
    return last.get(0).getEntryHash();
  }

  private static String hashEntry(String prev, AuditLogEntry e) {
    // Stable concatenation (avoid locale/timezone issues).
    String payload = String.join("|",
        "prev=" + (prev == null ? "" : prev),
        "at=" + (e.getCreatedAt() == null ? "" : e.getCreatedAt().toString()),
        "action=" + safe(e.getAction()),
        "actorUserId=" + safe(e.getActorUserId()),
        "actorEmail=" + safe(e.getActorEmail()),
        "targetUserId=" + safe(e.getTargetUserId()),
        "ip=" + safe(e.getIp()),
        "ua=" + safe(e.getUserAgent()),
        "details=" + safe(e.getDetails())
    );
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(payload.getBytes(StandardCharsets.UTF_8));
      return "0x" + HexFormat.of().formatHex(d);
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}

