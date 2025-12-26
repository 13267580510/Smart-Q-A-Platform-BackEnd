package org.example.backend.dto;

import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        boolean isForAllUsers,
        Long userId,
        String username,
        String notificationContent,
        LocalDateTime notificationTime
) {

}