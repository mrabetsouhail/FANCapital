package com.fancapital.backend.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

  public enum Type { PRICE, SECURITY, MARGIN }
  public enum Priority { LOW, MEDIUM, HIGH }

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 20)
  private Type type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority = Priority.MEDIUM;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }
  public Type getType() { return type; }
  public void setType(Type type) { this.type = type; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public boolean isRead() { return read; }
  public void setRead(boolean read) { this.read = read; }
  public Priority getPriority() { return priority; }
  public void setPriority(Priority priority) { this.priority = priority; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
