package org.example.backend.service;

import org.example.backend.dto.AdminNotificationListDTO;
import org.example.backend.dto.PageResponse;
import org.example.backend.model.Notification;
import org.example.backend.repository.AdminNotificationRepository;
import org.example.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminNotificationService {
    private final AdminNotificationRepository adminNotificationRepository;
    private final UserRepository userRepository;
    public AdminNotificationService(
            AdminNotificationRepository adminNotificationRepository,
            UserRepository userRepository) {
        this.adminNotificationRepository = adminNotificationRepository;
        this.userRepository = userRepository;
    }

    // 发布通知给所有用户
    public Notification publishNotificationToAllUsers(String content) {
        Notification notification = new Notification();
        System.out.println("content: " + content);
        notification.setNotificationContent(content);
        notification.setNotificationTime(LocalDateTime.now());
        return   adminNotificationRepository.save(notification);

    }

    // 发布通知给指定用户
    public Notification publishNotificationToUser(Long userId, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setNotificationContent(content);
        notification.setNotificationTime(LocalDateTime.now());
        return adminNotificationRepository.save(notification);
    }

    // 获取所有通知

    // 获取所有通知（分页）
    public PageResponse<AdminNotificationListDTO> getAllNotifications(int page, int size) {
        Pageable pageable = PageRequest.of(page , size); // 后端页码从0开始
        Page<Notification> notificationPage = adminNotificationRepository.findAll(pageable);
        List<AdminNotificationListDTO> dtos = notificationPage.getContent().stream()
                .map(notification -> AdminNotificationListDTO.fromNotification(notification, userRepository))
                .toList();
        return PageResponse.fromPage(notificationPage.map(n -> AdminNotificationListDTO.fromNotification(n, userRepository)));
    }

    // 删除通知
    // 删除通知
    public boolean deleteNotification(Long id) {
        if (adminNotificationRepository.existsById(id)) {
            adminNotificationRepository.deleteById(id);
            return true;
        }
        return false;
    }
    // 修改通知
    public Notification updateNotification(Long id, Map<String, Object> request) {
        return adminNotificationRepository.findById(id)
                .map(notification -> {
                    Long userId;
                    if (request.get("userId") instanceof Integer) {
                        userId  = ((Integer) request.get("userId")).longValue();
                    } else if (request.get("userId") instanceof Long) {
                        userId = (Long) request.get("userId");
                    } else if(request.get("userId") instanceof String) {
                        // 处理其他类型或者设置默认值
                        userId =  Long.parseLong((String) request.get("userId"));
                    }else{
                        userId =  null;
                    }

                    if(request.get("userId")==null){
                        notification.setUserId(null);
                    notification.setNotificationContent(request.get("notificationContent").toString());
                    notification.setNotificationTime(java.time.LocalDateTime.now());
                    }else{
                        notification.setUserId(userId);
                        notification.setNotificationContent(request.get("notificationContent").toString());
                        notification.setNotificationTime(java.time.LocalDateTime.now());
                    }
                    return adminNotificationRepository.save(notification);
                })
                .orElse(null);
    }
}