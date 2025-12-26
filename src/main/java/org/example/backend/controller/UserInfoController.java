package org.example.backend.controller;

import org.example.backend.dto.PageResponse;
import org.example.backend.dto.QuestionResponseDTO;
import org.example.backend.dto.UserReplyDTO;
import org.example.backend.dto.UserDTO;
import org.example.backend.model.Answer;
import org.example.backend.model.Notification;
import org.example.backend.model.User;
import org.example.backend.model.UserAvatar;
import org.example.backend.repository.QuestionRepository;
import org.example.backend.service.AnswerService;
import org.example.backend.service.NotificationService;
import org.example.backend.service.UserService;
import org.example.backend.utils.ApiResponse;
import org.example.backend.utils.JwtUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/UserInfo")
public class UserInfoController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final AnswerService answerService;
    private final NotificationService notificationService; // 注入通知服务

    public UserInfoController(UserService userService,JwtUtils jwtUtils,AnswerService answerService,NotificationService notificationService) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.answerService = answerService;
        this.notificationService = notificationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse> getUserInfo(@PathVariable Long userId, @RequestHeader("Authorization") String Authorization) {
        try {
            // 从token中获取当前用户ID
             String  token = Authorization.substring(7);
             System.out.println("token:"+token);
            Long currentUserId = jwtUtils.getUserIdFromToken(token);
            // 检查当前用户是否有权限获取目标用户信息
            if (!currentUserId.equals(userId) &&!jwtUtils.isUserAdmin(token)) {
                return ResponseEntity.status(403).body(ApiResponse.error(
                        403,
                        "没有权限获取该用户信息"
                ));
            }
            // 获取用户信息
            UserDTO userDTO  =   userService.findById(userId);

            if (userDTO != null) {
                return ResponseEntity.ok(ApiResponse.success(
                        200,
                        "获取用户信息成功",
                        userDTO
                ));
            } else {
                return ResponseEntity.status(404).body(ApiResponse.error(
                        404,
                        "未找到该用户"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "获取用户信息时发生错误: " + e.getMessage()
            ));
        }
    }



    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse> getUserNotifications(
            @RequestHeader("Authorization") String Authorization,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        try {
            // 从token中获取当前用户ID
            String token = Authorization.substring(7);
            Long userId = jwtUtils.getUserIdFromToken(token);

            // 获取用户通知（包括userId为null的全局通知）
            Page<Notification> notifications = notificationService.getUserNotifications(userId, page, size);

            return ResponseEntity.ok(ApiResponse.success(
                    200,
                    "获取通知成功",
                    PageResponse.fromPage(notifications)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "获取通知时发生错误: " + e.getMessage()
            ));
        }
    }

    /**
     * 修改用户个人信息
     * @param userId 用户ID
     * @param user 包含要更新信息的用户对象
     * @return 更新结果的响应
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse> updateUserInfo(@PathVariable("userId") Long userId, @RequestBody User user) {
        try {
            // 调用 UserService 中的更新方法
            UserDTO updatedUser = userService.updateUserInfo(userId, user);
            if (updatedUser != null) {
                return ResponseEntity.ok(ApiResponse.success(
                        200,
                        "用户信息更新成功",
                        updatedUser
                ));
            } else {
                return ResponseEntity.status(404).body(ApiResponse.error(
                        404,
                        "未找到该用户"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "更新用户信息时发生错误: " + e.getMessage()
            ));
        }
    }



    @PostMapping("/{userId}/avatar/upload")
    public ResponseEntity<ApiResponse> uploadUserAvatar(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String Authorization,
            @RequestParam("avatarFile") MultipartFile avatarFile) {
        try {
            // 从token中获取当前用户ID
             String token = Authorization.substring(7);
            Long currentUserId = jwtUtils.getUserIdFromToken(token);
            // 检查当前用户是否有权限上传目标用户的头像
            if (!currentUserId.equals(userId) &&!jwtUtils.isUserAdmin(token)) {
                return ResponseEntity.status(403).body(ApiResponse.error(
                        403,
                        "没有权限上传该用户的头像"
                ));
            }
            // 调用服务层上传头像方法
            String imageUrl = userService.uploadUserAvatar(userId, avatarFile);

                return ResponseEntity.ok(ApiResponse.success(
                        200,
                        "头像上传成功",
                        imageUrl
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "头像上传时发生错误: " + e.getMessage()
            ));
        }
    }



    @GetMapping("/{userId}/answers")
    public ResponseEntity<ApiResponse> getUserAnswers( @PathVariable("userId") Long userId,
                                                       @RequestHeader("Authorization") String Authorization,
                                                       @RequestParam(name = "page" ,defaultValue = "1") int page,
                                                       @RequestParam(name = "size",defaultValue = "10") int size) {
        try {
            // 从token中获取当前用户ID
            String token = Authorization.substring(7);
            Long currentUserId = jwtUtils.getUserIdFromToken(token);
            Pageable pageable = PageRequest.of(page - 1, size);

            // 检查权限：用户可以查看自己的回答，管理员可以查看任何用户的回答
            if (!currentUserId.equals(userId) && !jwtUtils.isUserAdmin(token)) {
                return ResponseEntity.status(403).body(ApiResponse.error(
                        403,
                        "没有权限查看该用户的回答"
                ));
            }

            // 获取用户的所有回答
            Page<UserReplyDTO> answersDTO = userService.getUserReplies(userId, pageable);
            System.out.println("pages:"+answersDTO.getTotalPages());
            System.out.println("total:"+answersDTO.getTotalElements());

            return ResponseEntity.ok(ApiResponse.success(
                    200,
                    "获取回答成功",
                    PageResponse.fromPage(
                            answersDTO
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "获取回答时发生错误: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/replies/{replyId}")
    public ResponseEntity<ApiResponse> deleteReply(
            @PathVariable("replyId") Long replyId,
            @RequestHeader("Authorization") String Authorization) {
        try {
            // 从token中获取当前用户ID
            String cleanToken = Authorization.substring(7);
            Long currentUserId = jwtUtils.getUserIdFromToken(cleanToken);

            // 调用服务层方法删除回答或评论，并进行鉴权
            boolean success = answerService.deleteReply(replyId, currentUserId);

            if (success) {
                return ResponseEntity.ok(ApiResponse.success(
                        200,
                        "删除成功"
                ));
            } else {
                return ResponseEntity.status(403).body(ApiResponse.error(
                        403,
                        "没有权限删除该回复"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                    500,
                    "删除回复时发生错误: " + e.getMessage()
            ));
        }
    }

    /*
    * 修改用户密码
    * */
    @PutMapping("/update/password")
    public ApiResponse changeUserPassword(@RequestHeader("Authorization") String Authorization,
                                          @RequestBody Map<String, Object> request) {
        try {
            String token = Authorization.substring(7);
            Long currentUserId = jwtUtils.getUserIdFromToken(token);

            // 调用服务层方法修改密码
            return  userService.changePassword(currentUserId,request);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}