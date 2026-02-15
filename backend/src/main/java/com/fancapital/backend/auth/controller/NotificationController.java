package com.fancapital.backend.auth.controller;

import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.model.Notification.Type;
import com.fancapital.backend.auth.service.NotificationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(defaultValue = "50") int limit) {
    String userId = currentUserIdOrThrow();
    var items = notificationService.listForUser(userId, limit);
    long unread = notificationService.unreadCount(userId);
    return ResponseEntity.ok(Map.of("items", items, "unreadCount", unread));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<?> markAsRead(@PathVariable String id) {
    String userId = currentUserIdOrThrow();
    notificationService.markAsRead(userId, id);
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  /** Déclenche une alerte marge LTV si le ratio dépasse 75%. Appelé par le frontend (page AST). */
  @PostMapping("/margin-alert")
  public ResponseEntity<?> marginAlert(@RequestBody(required = false) Map<String, Object> body) {
    String userId = currentUserIdOrThrow();
    Object ltvObj = body != null ? body.get("ltvPercent") : null;
    double ltv = ltvObj != null ? ((Number) ltvObj).doubleValue() : 0;
    if (ltv >= 75) {
      String msg = ltv >= 85
          ? "Votre ratio LTV est à " + String.format("%.1f", ltv) + "%. Risque de liquidation imminent."
          : "Votre ratio LTV est à " + String.format("%.1f", ltv) + "%. Risque de liquidation si le prix baisse.";
      notificationService.create(userId, Type.MARGIN, "Alerte de Marge - LTV Critique", msg, Priority.HIGH);
    }
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  private static String currentUserIdOrThrow() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new IllegalArgumentException("Unauthorized");
    }
    String userId = String.valueOf(auth.getPrincipal());
    if (userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)) {
      throw new IllegalArgumentException("Unauthorized");
    }
    return userId;
  }
}
