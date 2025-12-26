package org.example.backend.service;

import org.example.backend.model.Notification;
import org.example.backend.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // 获取用户通知，包括userId为null的全局通知
    public Page<Notification> getUserNotifications(Long userId, int page, int size) {
        // 按通知时间降序排列（最新的在前）
        Sort sort = Sort.by(Sort.Direction.DESC, "notificationTime");
        PageRequest pageRequest = PageRequest.of(page - 1, size, sort);

        // 查询该用户的通知或userId为null的全局通知
        return notificationRepository.findByUserIdOrUserIdIsNull(userId, pageRequest);
    }
}