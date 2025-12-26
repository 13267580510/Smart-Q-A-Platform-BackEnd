package org.example.backend.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.sun.jdi.Type;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.service.ai.ChatService;
import org.example.backend.service.ai.memory.RedisChatMemoryManager;
import org.example.backend.service.ai.memory.RedisChatMemoryStore;
import org.example.backend.service.ai.session.SessionManager;
import org.example.backend.utils.AiTypeConversion;
import org.example.backend.utils.ApiResponse;
import org.example.backend.utils.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import dev.langchain4j.memory.ChatMemory;
@RestController
@RequestMapping("/api/chat")
public class AichatController {

    private final ChatService chatService;
    private final SessionManager sessionManager;
    private final RedisChatMemoryManager memoryManager;
    RedisChatMemoryStore chatMemoryStore;
    private final AiTypeConversion aiTypeConversion;  // 添加这个
    public AichatController(ChatService chatService,
                            SessionManager sessionManager,
                            RedisChatMemoryManager memoryManager,
                            RedisChatMemoryStore chatMemoryStore,
                            AiTypeConversion aiTypeConversion
                            ) {
        this.chatService = chatService;
        this.sessionManager = sessionManager;
        this.memoryManager = memoryManager;
        this.chatMemoryStore = chatMemoryStore;
        this.aiTypeConversion = aiTypeConversion;  // 初始化
    }

    /**
     * 从Authorization头中提取用户ID
     */
    @GetMapping("/sse")
    public Flux<ServerSentEvent<Object>> sseChat(
            @RequestParam(required = false) String memoryId,
            @RequestParam String message,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        // 从token中获取当前用户ID
        String token = authorizationHeader.substring(7);
        Long userId = JwtUtils.getUserIdFromToken(token);

        System.out.println("接收到用户发来的请求开始处理AI对话聊天");
        System.out.println("用户ID: " + userId);
        System.out.println("用户消息: " + message);

        // 获取或创建会话ID（关联用户）
        String finalMemoryId = sessionManager.getOrCreateSession(memoryId, String.valueOf(userId));

        // 更新消息计数
        sessionManager.incrementMessageCount(finalMemoryId);

        // 打印会话信息
        System.out.println("会话ID: " + finalMemoryId);

        // 调用AI服务处理（ChatService会自动处理内存）
        return chatService.sseChat(finalMemoryId, message)
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
                                        "sessionId", finalMemoryId,
                                        "userId", userId,
                                        "expiryTime", LocalDateTime.now().plusHours(24),
                                        "message", "对话流已结束"
                                )))
                                .build()
                )
                .onErrorResume(e -> {
                    System.err.println("AI对话处理失败: " + e.getMessage());
                    e.printStackTrace();
                    return Flux.just(
                            ServerSentEvent.<Object>builder()
                                    .event("error")
                                    .data(ApiResponse.error(500, "处理失败: " + e.getMessage()))
                                    .build()
                    );
                });
    }

    @PostMapping("/session/new")
    public Mono<ResponseEntity<ApiResponse>> createNewSession(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            // 验证Authorization头
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }

            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            // 创建新会话
            String sessionId = sessionManager.getOrCreateSession(null, String.valueOf(userId));

            // 返回 ResponseEntity 包装的 ApiResponse
            return ResponseEntity.ok(
                    ApiResponse.success(
                            200,
                            "新会话创建成功",
                            Map.of(
                                    "sessionId", sessionId,
                                    "userId", userId,
                                    "createdAt", LocalDateTime.now()
                            )
                    )
            );

        }).onErrorResume(e ->
                Mono.just(ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(500, "创建新会话失败: " + e.getMessage())))
        );
    }
    // 结束会话（用户主动删除）
    @DeleteMapping("/session/{sessionId}")
    public Mono<ApiResponse> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            // 验证会话所有权
            SessionManager.ChatSession session = sessionManager.getSessionInfo(sessionId);
            if (session != null && !"anonymous".equals(userId)) {
                if (!String.valueOf(userId).equals(session.getUserId())) {
                    return ApiResponse.error(403, "无权删除此会话");
                }
            }

            // 清理聊天内存
            memoryManager.clearChatMemory(sessionId);

            // 删除会话
            sessionManager.removeSession(sessionId);

            return ApiResponse.success(200, "会话已删除", Map.of(
                    "sessionId", sessionId,
                    "userId", userId,
                    "deletedAt", LocalDateTime.now()
            ));
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "删除会话失败: " + e.getMessage()))
        );
    }

    // 获取会话信息
    @GetMapping("/session/{sessionId}/info")
    public Mono<ApiResponse> getSessionInfo(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            // 检查会话是否有效
            boolean isValid = sessionManager.isValidSession(sessionId);

            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", sessionId);
            data.put("isValid", isValid);

            if (isValid) {
                // 获取会话详细信息
                SessionManager.ChatSession session = sessionManager.getSessionInfo(sessionId);
                if (session != null) {
                    data.put("userId", session.getUserId());
                    data.put("title", session.getTitle());
                    data.put("createdAt", session.getCreatedAt());
                    data.put("lastAccessed", session.getLastAccessed());
                    data.put("messageCount", session.getMessageCount());
                    data.put("expiryTime", session.getExpiryTime());

                    // 检查权限
                    if (!"anonymous".equals(userId) && !String.valueOf(userId).equals(session.getUserId())) {
                        data.put("hasPermission", false);
                    } else {
                        data.put("hasPermission", true);
                    }
                }
            }

            return ApiResponse.success(200, "获取会话信息成功", data);
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "获取会话信息失败: " + e.getMessage()))
        );
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<ApiResponse>> getUserSessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            Authentication authentication,
            HttpServletResponse httpResponse) { // 添加这个参数

        System.out.println("Authentication: " + authentication);
        System.out.println("Principal: " + (authentication != null ? authentication.getPrincipal() : "null"));
        System.out.println("Authorities: " + (authentication != null ? authentication.getAuthorities() : "null"));
        return Mono.fromCallable(() -> {
            System.out.println("已接收到用户发送的获取所有sessions的请求");

            // 1. 验证Authorization头
            if (authorizationHeader == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "缺少认证令牌，请先登录"));
            }

            if (!authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "认证令牌格式不正确，应为Bearer token"));
            }

            // 2. 提取token
            String token = authorizationHeader.substring(7);
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "Token不能为空"));
            }

            try {
                // 3. 从token中获取当前用户ID
                Long userId = JwtUtils.getUserIdFromToken(token);

                // 4. 验证用户ID
                if (userId == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error(401, "无效的认证令牌"));
                }
                System.out.println("令牌有效");
                // 5. 获取用户的所有会话
                List<SessionManager.ChatSession> sessions = sessionManager.getUserSessions(String.valueOf(userId));
                System.out.println("已找到所有会话");

                // 6. 转换为响应格式
                List<Map<String, Object>> sessionList = sessions.stream()
                        .map(session -> {
                            Map<String, Object> sessionData = new HashMap<>();
                            sessionData.put("sessionId", session.getSessionId());
                            sessionData.put("title", session.getTitle());
                            sessionData.put("userId", session.getUserId());
                            sessionData.put("createdAt", session.getCreatedAt());
                            sessionData.put("lastAccessed", session.getLastAccessed());
                            sessionData.put("messageCount", session.getMessageCount());
                            sessionData.put("expiryTime", session.getExpiryTime());
                            sessionData.put("isValid", sessionManager.isValidSession(session.getSessionId()));
                            return sessionData;
                        })
                        .collect(Collectors.toList());
                System.out.println("转换格式成功");
                Map<String, Object> data = new HashMap<>();
                data.put("userId", userId);
                data.put("total", sessionList.size());
                data.put("sessions", sessionList);
                data.put("validCount", sessionList.stream()
                        .filter(s -> (Boolean) s.get("isValid"))
                        .count());
                System.out.println("准备发送回包");

                // 7. 返回成功响应

                ApiResponse response = ApiResponse.success(200, "获取用户会话列表成功", data);
                System.out.println("回包构建完毕");

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                // JWT解析错误或其他业务异常
                if (e instanceof JWTVerificationException || e.getCause() instanceof JWTVerificationException) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error(401, "认证令牌已过期或无效"));
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(500, "获取用户会话列表失败: " + e.getMessage()));
            }

        }).doOnSuccess(responseEntity -> {
            System.out.println("Mono成功，响应状态: " + responseEntity.getStatusCodeValue());
        }).onErrorResume(e -> {
            // 处理未捕获的异常
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "服务器内部错误: " + e.getMessage())));
        });
    }

    @PutMapping("/session/{sessionId}/title")
    public Mono<ApiResponse> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> requestBody,  // 改为 @RequestBody
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 从请求体中获取 title
            String title = requestBody.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ApiResponse.error(400, "标题不能为空");
            }

            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);
            System.out.println("收到修改会话标题的请求");

            // 验证会话所有权
            SessionManager.ChatSession session = sessionManager.getSessionInfo(sessionId);
            if (session == null) {
                return ApiResponse.error(404, "会话不存在");
            }
            System.out.println("userId"+userId);
            System.out.println("session"+session.getUserId());
            if (!"anonymous".equals(userId) && !String.valueOf(userId).equals(session.getUserId())) {
                return ApiResponse.error(403, "无权修改此会话");
            }

            // 更新标题
            session.setTitle(title);
            session.setLastAccessed(LocalDateTime.now());

            // 保存到Redis
            String key = "chat:session:" + sessionId;
            sessionManager.getRedisTemplate().opsForValue().set(key, session, Duration.ofHours(24));

            // 更新缓存
            sessionManager.getSessionCache().put(sessionId, session);

            return ApiResponse.success(200, "会话标题更新成功", Map.of(
                    "sessionId", sessionId,
                    "userId", userId,
                    "title", title,
                    "updatedAt", LocalDateTime.now()
            ));
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "更新会话标题失败: " + e.getMessage()))
        );
    }
    // 清理所有过期会话
    @PostMapping("/sessions/cleanup")
    public Mono<ApiResponse> cleanupExpiredSessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            if (!"anonymous".equals(userId)) {
                // 清理指定用户的过期会话
                sessionManager.cleanupUserExpiredSessions(String.valueOf(userId));
                return ApiResponse.success(200, "用户过期会话已清理", Map.of(
                        "userId", userId,
                        "cleanupTime", LocalDateTime.now()
                ));
            } else {
                // 清理所有过期会话
                sessionManager.cleanupExpiredSessions();
                return ApiResponse.success(200, "所有过期会话已清理", Map.of(
                        "cleanupTime", LocalDateTime.now(),
                        "note", "Redis中的过期key会自动清理，本次主要清理内存缓存"
                ));
            }
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "清理会话失败: " + e.getMessage()))
        );
    }

    /**
     * 获取指定会话的所有对话记录
     */
    @GetMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        return Mono.fromCallable(() -> {
            System.out.println("获取会话对话记录请求: " + sessionId);

            // 1. 验证Authorization头
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "请提供有效的认证令牌"));
            }
            System.out.println("");
            // 2. 从token中获取用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            // 3. 验证会话存在性和所有权
            SessionManager.ChatSession session = sessionManager.getSessionInfo(sessionId);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "会话不存在或已过期"));
            }
            System.out.println("userId: " +  userId);
            System.out.println("session.getUserId(): " + session.getUserId());
            // 4. 检查权限（用户只能查看自己的会话）
            if (!"anonymous".equals(userId) && !String.valueOf(userId).equals(session.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(403, "无权访问此会话"));
            }

            // 5. 获取会话中的消息 - 使用 memoryManager.getMessages()
            List<ChatMessage> messages = chatMemoryStore.getMessages(sessionId);
            System.out.println(messages);
            // 6. 格式化消息为前端需要的格式
            List<Map<String, Object>> formattedMessages = formatChatMessages(messages);

            // 7. 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("sessionId", sessionId);
            responseData.put("userId", userId);
            responseData.put("title", session.getTitle());
            responseData.put("totalMessages", formattedMessages.size());
            responseData.put("messages", formattedMessages);
            responseData.put("sessionInfo", Map.of(
                    "createdAt", session.getCreatedAt(),
                    "lastAccessed", session.getLastAccessed(),
                    "messageCount", session.getMessageCount()
            ));

            return ResponseEntity.ok(
                    ApiResponse.success(200, "获取会话消息成功", responseData)
            );

        }).onErrorResume(e -> {
            System.err.println("获取会话消息失败: " + e.getMessage());
            e.printStackTrace();

            if (e instanceof JWTVerificationException || e.getCause() instanceof JWTVerificationException) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(401, "认证令牌已过期或无效")));
            }

            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "获取会话消息失败: " + e.getMessage())));
        });
    }
    /**
     * 格式化聊天消息，将ChatMessage对象转换为前端友好的格式
     */
    /**
     * 格式化聊天消息，将ChatMessage对象转换为前端友好的格式
     * 过滤掉SystemMessage，不返回给前端
     */
    private List<Map<String, Object>> formatChatMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        int messageIndex = 1;

        for (ChatMessage message : messages) {
            try {
                // 跳过SystemMessage，不返回给前端
                if (message instanceof SystemMessage) {
                    System.out.println("跳过SystemMessage，不返回给前端");
                    continue;
                }

                Map<String, Object> formattedMessage = new HashMap<>();

                // 消息基本信息
                formattedMessage.put("index", messageIndex++);
                formattedMessage.put("type", message.type().name());

                // 提取纯文本内容
                String textContent = AiTypeConversion.extractTextFromMessage(message);
                formattedMessage.put("content", textContent);
                formattedMessage.put("timestamp", LocalDateTime.now());

                // 根据消息类型添加角色信息
                if (message instanceof UserMessage) {
                    formattedMessage.put("role", "user");
                } else if (message instanceof AiMessage) {
                    formattedMessage.put("role", "assistant");
                    AiMessage aiMessage = (AiMessage) message;
                    formattedMessage.put("hasToolExecution", aiMessage.hasToolExecutionRequests());
                }
                // SystemMessage已经被跳过，不需要处理

                formattedMessages.add(formattedMessage);

            } catch (Exception e) {
                System.err.println("格式化消息时出错: " + e.getMessage());
            }
        }

        System.out.println("格式化后返回消息数量: " + formattedMessages.size() + "（过滤了SystemMessage）");
        return formattedMessages;
    }

    // 获取会话统计信息
    @GetMapping("/sessions/stats")
    public Mono<ApiResponse> getSessionStats(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return Mono.fromCallable(() -> {
            // 从token中获取当前用户ID
            String token = authorizationHeader.substring(7);
            Long userId = JwtUtils.getUserIdFromToken(token);

            Map<String, Object> data = new HashMap<>();

            if (!"anonymous".equals(userId)) {
                // 获取用户特定统计
                long userSessionCount = sessionManager.getUserSessionCount(String.valueOf(userId));
                data.put("userId", userId);
                data.put("userSessionCount", userSessionCount);
                data.put("totalSessions", sessionManager.getActiveSessionCount());
            } else {
                // 获取全局统计
                data.put("totalSessions", sessionManager.getActiveSessionCount());
                data.put("note", "活跃会话数量基于内存缓存，重启后会重置");
            }

            data.put("currentTime", LocalDateTime.now());

            return ApiResponse.success(200, "获取会话统计成功", data);
        }).onErrorResume(e ->
                Mono.just(ApiResponse.error(500, "获取会话统计失败: " + e.getMessage()))
        );
    }
}