package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.model.Notification;
import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.model.Notification.Type;
import com.fancapital.backend.auth.repo.NotificationRepository;
import com.fancapital.backend.auth.repo.AppUserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository repo;
  private final AppUserRepository userRepo;

  public NotificationService(NotificationRepository repo, AppUserRepository userRepo) {
    this.repo = repo;
    this.userRepo = userRepo;
  }

  public List<Map<String, Object>> listForUser(String userId, int limit) {
    if (limit <= 0) limit = 50;
    if (limit > 100) limit = 100;
    return repo.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
        .stream()
        .map(this::toMap)
        .collect(Collectors.toList());
  }

  public long unreadCount(String userId) {
    return repo.countByUser_IdAndReadFalse(userId);
  }

  @Transactional
  public void markAsRead(String userId, String notificationId) {
    repo.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1000))
        .stream()
        .filter(n -> n.getId().equals(notificationId))
        .findFirst()
        .ifPresent(n -> {
          n.setRead(true);
          repo.save(n);
        });
  }

  /** Crée une notification pour l'utilisateur. */
  public Notification create(String userId, Type type, String title, String message, Priority priority) {
    AppUser user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    var n = new Notification();
    n.setUser(user);
    n.setType(type);
    n.setTitle(title);
    n.setMessage(message);
    n.setPriority(priority != null ? priority : Priority.MEDIUM);
    return repo.save(n);
  }

  private Map<String, Object> toMap(Notification n) {
    return Map.of(
        "id", n.getId(),
        "type", n.getType().name().toLowerCase(),
        "title", n.getTitle(),
        "message", n.getMessage(),
        "read", n.isRead(),
        "priority", n.getPriority().name().toLowerCase(),
        "timestamp", n.getCreatedAt().toString()
    );
  }
}
