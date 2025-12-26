package org.example.backend.controller;

import org.apache.coyote.Request;
import org.example.backend.dto.AdminNotificationListDTO;
import org.example.backend.dto.PageResponse;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.Notification;
import org.example.backend.service.AdminNotificationService;
import org.example.backend.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.example.backend.model.ResponseStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    @Autowired
    private AdminNotificationService adminNotificationService;

    /**
     * 发布通知给所有用户
     * @param request 通知内容
     * @return 封装了发布结果的 ApiResponse 对象
     */
    @PostMapping("/all")
    public ApiResponse publishNotificationToAllUsers(@RequestBody Map<String, Object> request) {
        try {
            Notification result = adminNotificationService.publishNotificationToAllUsers((String) request.get("content"));
            if(result != null){
                return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "通知已成功发布给指定用户",result);
            }else {
                return ApiResponse.error(ResponseStatus.OPERATION_FAILED.getCode(), "操作失败");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "发布通知给所有用户时出错: " + e.getMessage());
        }
    }

    /**
     * 发布通知给指定用户
     * @param userId 用户 ID
     * @param request 通知内容
     * @return 封装了发布结果的 ApiResponse 对象
     */
    @PostMapping("/user/{userId}")
    public ApiResponse publishNotificationToUser(@PathVariable Long userId, Map<String, Object> request) {
        try {
            Notification result = adminNotificationService.publishNotificationToUser(userId, (String) request.get("content"));
            if(result != null){
                return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "通知已成功发布给指定用户",result);
            }else {
                return ApiResponse.error(ResponseStatus.OPERATION_FAILED.getCode(), "操作失败");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "发布通知给指定用户时出错: " + e.getMessage());
        }
    }


    /**
     * 获取所有通知（分页）
     * @param page 页码
     * @param size 每页数量
     * @return 封装了分页通知结果的 ApiResponse 对象
     */
    @GetMapping("/List")
    public ApiResponse getAllNotifications(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        try {
            PageResponse<AdminNotificationListDTO> result = adminNotificationService.getAllNotifications(page-1, size);
            return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "获取所有通知成功", result);
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "获取所有通知时出错: " + e.getMessage());
        }
    }

    /**
     * 修改通知
     * @param id 通知 ID
     * @param request 新的通知内容对象
     * @return 封装了修改结果的 ApiResponse 对象
     */
    @PutMapping("/{id}")
    public ApiResponse updateNotification(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Notification result = adminNotificationService.updateNotification(id,  request);
            if (result != null) {
                return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "通知已成功修改", result);
            } else {
                return ApiResponse.error(ResponseStatus.OPERATION_FAILED.getCode(), "操作失败，未找到指定通知");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "修改通知时出错: " + e.getMessage());
        }
    }

    /**
     * 删除通知
     * @param id 通知 ID
     * @return 封装了删除结果的 ApiResponse 对象
     */
    @DeleteMapping("/{id}")
    public ApiResponse deleteNotification(@PathVariable Long id) {
        try {
            boolean isDeleted = adminNotificationService.deleteNotification(id);
            if (isDeleted) {
                return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "通知已成功删除", null);
            } else {
                return ApiResponse.error(ResponseStatus.OPERATION_FAILED.getCode(), "通知不存在，删除失败");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "删除通知时出错: " + e.getMessage());
        }
    }
}