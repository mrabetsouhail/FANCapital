package com.fancapital.backend.auth.repo;

import com.fancapital.backend.auth.model.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
  List<Notification> findByUser_IdOrderByCreatedAtDesc(String userId, org.springframework.data.domain.Pageable pageable);
  long countByUser_IdAndReadFalse(String userId);
}
