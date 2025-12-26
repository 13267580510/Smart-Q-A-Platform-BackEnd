package org.example.backend.dto;

import org.example.backend.model.Notification;
import org.example.backend.repository.UserRepository;
import org.example.backend.service.UserService;

import java.io.Serializable;
import java.time.LocalDateTime;

public record AdminNotificationListDTO(
        Long id,
        boolean isForAllUsers,
        Long userId,
        String username, // 假设需要从用户服务中获取用户名，这里仅作占位
        String notificationContent,
        LocalDateTime notificationTime
) implements Serializable {
    public static AdminNotificationListDTO fromNotification(Notification notification, UserRepository userRepository) {
        boolean isForAllUsers = notification.getUserId() == null;
        Long userId = notification.getUserId();
        String username = isForAllUsers ? "所有用户" : userRepository.findById(userId).get().getUsername();
        String content = notification.getNotificationContent();
        LocalDateTime time = notification.getNotificationTime();
        return new AdminNotificationListDTO(notification.getId(),isForAllUsers, userId, username, content, time);
    }
}