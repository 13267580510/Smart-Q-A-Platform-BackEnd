package org.example.backend.repository;

import org.example.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNotificationRepository extends JpaRepository<Notification, Long> {
}