package org.example.backend.repository;

import org.example.backend.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 查询指定用户的通知或userId为null的全局通知
    Page<Notification> findByUserIdOrUserIdIsNull(Long userId, Pageable pageable);
}