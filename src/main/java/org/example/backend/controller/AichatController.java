package org.example.backend.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import dev.langchain4j.data.message.ChatMessageType;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.dto.BatchSaveMessagesRequest;
import org.example.backend.dto.ChatMessageDTO;
import org.example.backend.dto.ChatSessionDTO;
import org.example.backend.model.ChatSessionEntity;
import org.example.backend.service.ai.ChatService;
import org.example.backend.service.ai.ChatMessageService;
import org.example.backend.service.ai.memory.HybridChatMemoryStore;
import org.example.backend.service.ai.session.ChatSessionService;
import org.example.backend.service.ai.session.SessionManager;
import org.example.backend.utils.ApiResponse;
import org.example.backend.utils.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class AichatController {

    private final ChatService chatService;
    private final SessionManager sessionManager;
    private final HybridChatMemoryStore hybridChatMemoryStore;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    public AichatController(ChatService chatService,
                            SessionManager sessionManager,
                            HybridChatMemoryStore hybridChatMemoryStore,
                            ChatSessionService chatSessionService,
                            ChatMessageService chatMessageService) {
        this.chatService = chatService;
        this.sessionManager = sessionManager;
        this.hybridChatMemoryStore = hybridChatMemoryStore;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
    }

    /**
     * SSE流式聊天（使用新的混合存储）
     */
    @GetMapping("/sse")
    public Flux<ServerSentEvent<Object>> sseChat(
            @RequestParam(required = false) String memoryId,
            @RequestParam String message,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                throw new SecurityException("未提供有效的认证令牌");
            }

            log.info("开始处理AI对话聊天: userId={}, messageLength={}", userId, message.length());
            System.out.println("memoryId={"+memoryId+"}");
            // 获取或创建会话ID（关联用户）
            String finalMemoryId = sessionManager.getOrCreateSession(memoryId, String.valueOf(userId));

            // 更新消息计数
            chatSessionService.incrementMessageCount(userId,finalMemoryId);

            log.debug("会话信息: sessionId={}, userId={}", finalMemoryId, userId);
            return Map.of("sessionId", finalMemoryId, "userId", userId);

        }).flatMapMany(sessionInfo -> {
            String sessionId = (String) sessionInfo.get("sessionId");
            Long userId = (Long) sessionInfo.get("userId");

            // 调用AI服务处理
            return chatService.sseChat(sessionId, message)
                    .map(chunk -> {
                        ApiResponse response = ApiResponse.success(200, "处理成功", chunk);
                        return ServerSentEvent.<Object>builder()
                                .event("message")
                                .id(UUID.randomUUID().toString())
                                .data(response)
                                .build();
                    })
                    .concatWithValues(
                            ServerSentEvent.<Object>builder()
                                    .event("complete")
                                    .data(ApiResponse.success(200, "对话完成", Map.of(
                                            "sessionId", sessionId,
                                            "userId", userId,
                                            "expiryTime", LocalDateTime.now().plusHours(24),
                                            "message", "对话流已结束"
                                    )))
                                    .build()
                    )
                    .onErrorResume(e -> {
                        log.error("AI对话处理失败: sessionId={}", sessionId, e);
                        return Flux.just(
                                ServerSentEvent.<Object>builder()
                                        .event("error")
                                        .data(ApiResponse.error(500, "处理失败: " + e.getMessage()))
                                        .build()
                        );
                    });
        });
    }

    /**
     * 创建新会话（使用新的ChatSessionService）
     */
    @PostMapping("/session/new")
    public Mono<ResponseEntity<ApiResponse>> createNewSession(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "title", required = false) String title) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            // 生成会话ID
            String sessionId = "session_" + System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().substring(0, 8);

            // 创建新会话（保存到MySQL和Redis）
            ChatSessionEntity session = chatSessionService.saveOrUpdateSession(sessionId, userId, title);

            // 返回响应
            Map<String, Object> data = Map.of(
                    "sessionId", session.getSessionId(),
                    "userId", session.getUserId(),
                    "title", session.getTitle(),
                    "createdAt", session.getCreatedAt(),
                    "expiryTime", session.getExpiryTime()
            );

            return ResponseEntity.ok(
                    ApiResponse.success(200, "新会话创建成功", data)
            );

        }).onErrorResume(e ->
                Mono.just(ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(500, "创建新会话失败: " + e.getMessage())))
        );
    }

    /**
     * 结束会话（使用新的服务）
     */
    @DeleteMapping("/session/{sessionId}")
    public Mono<ApiResponse> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ApiResponse.error(401, "请提供有效的认证令牌");
            }

            // 删除会话（包括MySQL和Redis中的数据）
            chatSessionService.deleteSession(sessionId, userId);

            return ApiResponse.success(200, "会话已删除", Map.of(
                    "sessionId", sessionId,
                    "userId", userId,
                    "deletedAt", LocalDateTime.now()
            ));
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "删除会话失败: " + e.getMessage()))
        );
    }

    /**
     * 获取会话信息（使用混合存储）
     */
    @GetMapping("/session/{sessionId}/info")
    public Mono<ApiResponse> getSessionInfo(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ApiResponse.error(401, "请提供有效的认证令牌");
            }

            // 获取会话信息
            ChatSessionEntity session = chatSessionService.getSession(userId,sessionId);
            if (session == null) {
                return ApiResponse.error(404, "会话不存在或已过期");
            }

            // 检查权限
            if (!session.getUserId().equals(userId)) {
                return ApiResponse.error(403, "无权访问此会话");
            }

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getSessionId());
            data.put("userId", session.getUserId());
            data.put("title", session.getTitle());
            data.put("createdAt", session.getCreatedAt());
            data.put("lastAccessed", session.getLastAccessed());
            data.put("messageCount", session.getMessageCount());
            data.put("expiryTime", session.getExpiryTime());
            data.put("isActive", session.getIsActive());
            data.put("isValid", session.getIsActive() != null && session.getIsActive()
                    && (session.getExpiryTime() == null || LocalDateTime.now().isBefore(session.getExpiryTime())));

            return ApiResponse.success(200, "获取会话信息成功", data);
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "获取会话信息失败: " + e.getMessage()))
        );
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<ApiResponse>> getUserSessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            log.debug("获取用户会话列表: userId={}", userId);

            List<ChatSessionEntity> sessions = chatSessionService.getUserSessions(userId);
            //List<ChatSessionEntity> sessions = chatSessionService.getUserSessionsWithoutCache(userId);

            // 诊断缓存问题
            chatSessionService.diagnoseCacheIssue(userId);

            // 转换为DTO列表
            List<ChatSessionDTO> sessionList = sessions.stream()
                    .map(session -> {
                        log.debug("转换会话: sessionId={}, class={}",
                                session.getSessionId(), session.getClass().getName());
                        return convertToSessionDTO(session);
                    })
                    .collect(Collectors.toList());

            // 统计信息
            int total = sessionList.size();
            long validCount = sessionList.stream()
                    .filter(ChatSessionDTO::getIsValid)
                    .count();

            Map<String, Object> data = Map.of(
                    "userId", userId,
                    "total", total,
                    "validCount", validCount,
                    "sessions", sessionList
            );

            return ResponseEntity.ok(
                    ApiResponse.success(200, "获取用户会话列表成功", data)
            );

        }).onErrorResume(e -> {
            log.error("获取用户会话列表失败", e);
            if (e instanceof JWTVerificationException) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "认证令牌已过期或无效")));
            }
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "获取用户会话列表失败: " + e.getMessage())));
        });
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{sessionId}/title")
    public Mono<ApiResponse> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> requestBody,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ApiResponse.error(401, "请提供有效的认证令牌");
            }

            String title = requestBody.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ApiResponse.error(400, "标题不能为空");
            }

            log.debug("更新会话标题: sessionId={}, userId={}, title={}", sessionId, userId, title);

            // 更新会话标题
            ChatSessionEntity updatedSession = chatSessionService.updateSessionTitle(sessionId, title, userId);

            return ApiResponse.success(200, "会话标题更新成功", Map.of(
                    "sessionId", updatedSession.getSessionId(),
                    "userId", updatedSession.getUserId(),
                    "title", updatedSession.getTitle(),
                    "updatedAt", LocalDateTime.now()
            ));
        }).onErrorResume(e -> {
            if (e instanceof SecurityException) {
                return Mono.just(ApiResponse.error(403, e.getMessage()));
            }
            return Mono.just(ApiResponse.error(500, "更新会话标题失败: " + e.getMessage()));
        });
    }

    @GetMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 1. 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            log.debug("获取会话消息: sessionId={}, userId={}", sessionId, userId);

            // 2. 获取会话信息用于权限验证
            ChatSessionEntity session = chatSessionService.getSession(userId, sessionId);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "会话不存在或已过期"));
            }

            // 3. 检查权限
            if (!session.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(403, "无权访问此会话"));
            }

            // 4. 获取原始消息列表（包含SYSTEM）
            List<ChatMessageDTO> allMessages = chatMessageService.getMessagesAsDTO(sessionId);

            // 5. 核心修正：过滤SYSTEM类型消息（字符串匹配，兼容空值）
            List<ChatMessageDTO> filteredMessages = allMessages.stream()
                    // 空值判断 + 字符串匹配（匹配你Redis中的"SYSTEM"）
                    .filter(dto -> dto.getType() != null && !"SYSTEM".equals(dto.getType()))
                    // 可选：同时过滤role为system的消息（双重保险）
                    // .filter(dto -> dto.getRole() == null || !"system".equals(dto.getRole().toLowerCase()))
                    .collect(Collectors.toList());

            // 6. 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("sessionId", sessionId);
            responseData.put("userId", userId);
            responseData.put("title", session.getTitle());
            responseData.put("totalMessages", allMessages.size()); // 原始总数（含SYSTEM）
            responseData.put("displayMessagesCount", filteredMessages.size()); // 前端展示数量
            responseData.put("messages", filteredMessages); // 过滤后给前端的消息
            responseData.put("sessionInfo", Map.of(
                    "createdAt", session.getCreatedAt(),
                    "lastAccessed", session.getLastAccessed(),
                    "messageCount", session.getMessageCount()
            ));

            // 7. 返回响应
            return ResponseEntity.ok(
                    ApiResponse.success(200, "获取会话消息成功", responseData)
            );

        }).onErrorResume(e -> {
            log.error("获取会话消息失败", e);
            if (e instanceof JWTVerificationException) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "认证令牌已过期或无效")));
            }
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "获取会话消息失败: " + e.getMessage())));
        });
    }


    /**
     * 批量保存消息（用于数据迁移或批量导入）
     */
    @PostMapping("/session/{sessionId}/messages/batch")
    public Mono<ResponseEntity<ApiResponse>> batchSaveMessages(
            @PathVariable String sessionId,
            @RequestBody BatchSaveMessagesRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            log.debug("批量保存消息: sessionId={}, userId={}, messageCount={}",
                    sessionId, userId, request.getMessages().size());

            // 确保sessionId一致
            request.setSessionId(sessionId);
            request.setUserId(userId);

            // 批量保存消息
            int savedCount = chatMessageService.saveMessages(request);

            return ResponseEntity.ok(
                    ApiResponse.success(200, "批量保存消息成功", Map.of(
                            "sessionId", sessionId,
                            "userId", userId,
                            "savedCount", savedCount,
                            "totalRequested", request.getMessages().size(),
                            "savedAt", LocalDateTime.now()
                    ))
            );

        }).onErrorResume(e -> {
            log.error("批量保存消息失败", e);
            if (e instanceof SecurityException) {
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(403, e.getMessage())));
            }
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "批量保存消息失败: " + e.getMessage())));
        });
    }

    /**
     * 导出会话消息（用于备份）
     */
    @GetMapping("/session/{sessionId}/export")
    public Mono<ResponseEntity<ApiResponse>> exportSessionMessages(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            log.debug("导出会话消息: sessionId={}, userId={}", sessionId, userId);

            // 导出消息
            Map<String, Object> exportData = chatMessageService.exportSessionMessages(sessionId, userId);

            return ResponseEntity.ok(
                    ApiResponse.success(200, "导出会话消息成功", exportData)
            );

        }).onErrorResume(e -> {
            log.error("导出会话消息失败", e);
            if (e instanceof SecurityException) {
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(403, e.getMessage())));
            }
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "导出会话消息失败: " + e.getMessage())));
        });
    }

    /**
     * 清理过期会话
     */
    @PostMapping("/sessions/cleanup")
    public Mono<ApiResponse> cleanupExpiredSessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);

            int cleanedCount;
            if (userId != null) {
                // 清理指定用户的过期会话
                cleanedCount = chatSessionService.cleanupExpiredSessions();
                log.info("清理用户过期会话: userId={}, count={}", userId, cleanedCount);

                return ApiResponse.success(200, "用户过期会话已清理", Map.of(
                        "userId", userId,
                        "cleanedCount", cleanedCount,
                        "cleanupTime", LocalDateTime.now()
                ));
            } else {
                // 清理所有过期会话
                cleanedCount = chatSessionService.cleanupExpiredSessions();
                log.info("清理所有过期会话: count={}", cleanedCount);

                return ApiResponse.success(200, "所有过期会话已清理", Map.of(
                        "cleanedCount", cleanedCount,
                        "cleanupTime", LocalDateTime.now(),
                        "note", "已清理过期会话，包括数据库和缓存"
                ));
            }
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "清理会话失败: " + e.getMessage()))
        );
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/sessions/stats")
    public Mono<ApiResponse> getSessionStats(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证和提取用户ID
            Long userId = extractUserId(authorizationHeader);

            Map<String, Object> data = new HashMap<>();
            data.put("currentTime", LocalDateTime.now());

            if (userId != null) {
                // 获取用户特定统计
                List<ChatSessionEntity> userSessions = chatSessionService.getUserSessions(userId);
                long activeCount = userSessions.stream()
                        .filter(session -> session.getIsActive() != null && session.getIsActive())
                        .filter(session -> session.getExpiryTime() == null ||
                                LocalDateTime.now().isBefore(session.getExpiryTime()))
                        .count();

                data.put("userId", userId);
                data.put("userSessionCount", userSessions.size());
                data.put("userActiveSessionCount", activeCount);
            }

            // 添加系统统计（这里可以添加更多统计信息）
            data.put("systemTime", LocalDateTime.now());
            data.put("note", "统计信息基于数据库查询，缓存用于加速访问");

            return ApiResponse.success(200, "获取会话统计成功", data);
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "获取会话统计失败: " + e.getMessage()))
        );
    }


    /**
     * 从Authorization头中提取用户ID
     */
    private Long extractUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        try {
            String token = authorizationHeader.substring(7);
            return JwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("解析JWT令牌失败", e);
            return null;
        }
    }

    /**
     * 转换会话实体为DTO
     */
    private ChatSessionDTO convertToSessionDTO(ChatSessionEntity entity) {
        return ChatSessionDTO.builder()
                .sessionId(entity.getSessionId())
                .title(entity.getTitle())
                .userId(entity.getUserId())
                .createdAt(entity.getCreatedAt())
                .lastAccessed(entity.getLastAccessed())
                .messageCount(entity.getMessageCount())
                .expiryTime(entity.getExpiryTime())
                .isValid(entity.getIsActive() != null && entity.getIsActive()
                        && (entity.getExpiryTime() == null || LocalDateTime.now().isBefore(entity.getExpiryTime())))
                .metadata(entity.getMetadata() != null ? entity.getMetadata() : new HashMap<>())
                .build();
    }
}