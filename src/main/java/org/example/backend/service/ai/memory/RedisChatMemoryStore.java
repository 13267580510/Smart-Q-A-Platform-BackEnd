package org.example.backend.service.ai.memory;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.example.backend.utils.AiTypeConversion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;
import java.util.*;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired  // 改为依赖注入
    private AiTypeConversion aiTypeConversion;  // 移除 new 创建
    @Autowired
    private RestTemplate restTemplate;  // 需要在配置类中定义

    @Value("${langchain4j.community.dashscope.chat-model.api-key}")
    private String apiKey;

    private static final String CHAT_MESSAGES_KEY_PREFIX = "chat:messages:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/api/v1";

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();

        // 1. 首先从Redis获取（快速缓存）
        List<ChatMessage> redisMessages = getMessagesFromRedis(sessionId);

        if (!redisMessages.isEmpty()) {
            System.out.println("从Redis获取到 " + redisMessages.size() + " 条消息");
            return redisMessages;
        }

        // 2. Redis中没有，从DashScope获取
        System.out.println("Redis中没有消息，尝试从DashScope获取会话: " + sessionId);
        List<ChatMessage> dashScopeMessages = getMessagesFromDashScope(sessionId);

        // 3. 将从DashScope获取的消息保存到Redis
        if (!dashScopeMessages.isEmpty()) {
            System.out.println("从DashScope获取到 " + dashScopeMessages.size() + " 条消息，保存到Redis");
            saveToRedis(sessionId, dashScopeMessages);
        }

        return dashScopeMessages;
    }

    /**
     * 从Redis获取消息
     */
    private List<ChatMessage> getMessagesFromRedis(String sessionId) {
        String key = CHAT_MESSAGES_KEY_PREFIX + sessionId;
        try {
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof List) {
                List<?> list = (List<?>) value;
                List<ChatMessage> messages = new ArrayList<>();

                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        String type = (String) map.get("type");
                        String text = (String) map.get("text");

                        if (type != null && text != null) {
                            ChatMessage message = createChatMessageFromMap(type, text);
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                    }
                }

                if (!messages.isEmpty()) {
                    redisTemplate.expire(key, DEFAULT_TTL);
                    return messages;
                }
            }
        } catch (Exception e) {
            System.err.println("从Redis获取消息失败: " + e.getMessage());
            e.printStackTrace();
        }
        return Collections.emptyList();
    }


    private ChatMessage createChatMessageFromMap(String type, String text) {
        switch (type) {
            case "USER":
                return UserMessage.from(text);
            case "AI":
                return AiMessage.from(text);
            case "SYSTEM":
                return SystemMessage.from(text);
            default:
                return null;
        }
    }

    /**
     * 将 Map 转换为 ChatMessage
     */
    private ChatMessage convertMapToChatMessage(Map<String, Object> map) {
        try {
            String type = (String) map.get("@type");
            String text = (String) map.get("text");

            if (type == null || text == null) {
                return null;
            }

            // 根据类型创建相应的 ChatMessage
            switch (type) {
                case "dev.langchain4j.data.message.UserMessage":
                    return UserMessage.from(text);
                case "dev.langchain4j.data.message.AiMessage":
                    return AiMessage.from(text);
                case "dev.langchain4j.data.message.SystemMessage":
                    return SystemMessage.from(text);
                default:
                    System.err.println("未知的 ChatMessage 类型: " + type);
                    return null;
            }
        } catch (Exception e) {
            System.err.println("转换 Map 到 ChatMessage 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从DashScope获取消息
     */
    private List<ChatMessage> getMessagesFromDashScope(String sessionId) {
        String url = DASHSCOPE_BASE_URL + "/sessions/{sessionId}/messages";

        try {
            // 创建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map.class,
                    sessionId
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object messagesObj = body.get("messages");

                if (messagesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dashScopeMessages = (List<Map<String, Object>>) messagesObj;

                    // 转换为LangChain4j的ChatMessage
                    List<ChatMessage> chatMessages = convertToChatMessages(dashScopeMessages);
                    System.out.println("成功从DashScope转换 " + chatMessages.size() + " 条消息");
                    return chatMessages;
                }
            }
        } catch (Exception e) {
            System.err.println("从DashScope获取消息失败: " + e.getMessage());
            // 这里不要抛出异常，返回空列表即可
        }

        return Collections.emptyList();
    }

    /**
     * 将DashScope的消息格式转换为ChatMessage
     */
    private List<ChatMessage> convertToChatMessages(List<Map<String, Object>> dashScopeMessages) {
        List<ChatMessage> chatMessages = new ArrayList<>();

        for (Map<String, Object> msg : dashScopeMessages) {
            try {
                String role = (String) msg.get("role");
                Object contentObj = msg.get("content");
                String content = contentObj != null ? contentObj.toString() : "";

                ChatMessage chatMessage;
                switch (role.toLowerCase()) {
                    case "user":
                        chatMessage = UserMessage.from(content);
                        break;
                    case "assistant":
                        chatMessage = AiMessage.from(content);
                        break;
                    case "system":
                        chatMessage = SystemMessage.from(content);
                        break;
                    default:
                        System.err.println("未知的消息角色: " + role);
                        continue;  // 跳过未知角色
                }

                chatMessages.add(chatMessage);
            } catch (Exception e) {
                System.err.println("转换消息失败: " + e.getMessage());
            }
        }

        return chatMessages;
    }

    /**
     * 保存消息到Redis
     */
    private void saveToRedis(String sessionId, List<ChatMessage> messages) {
        String key = CHAT_MESSAGES_KEY_PREFIX + sessionId;
        try {
            // 转换为可序列化的格式，过滤掉SystemMessage
            List<Map<String, Object>> serializableMessages = new ArrayList<>();

            for (ChatMessage message : messages) {
                // 跳过SystemMessage
                if (message instanceof SystemMessage) {
                    System.out.println("保存到Redis时跳过SystemMessage");
                    continue;
                }

                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("type", message.type().toString());

                // 提取并保存文本
                messageMap.put("text", AiTypeConversion.extractTextFromMessage(message));

                if (message instanceof UserMessage) {
                    messageMap.put("role", "user");
                } else if (message instanceof AiMessage) {
                    messageMap.put("role", "assistant");
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.hasToolExecutionRequests()) {
                        messageMap.put("hasTools", true);
                    }
                }
                // SystemMessage已经被跳过

                serializableMessages.add(messageMap);
            }

            redisTemplate.opsForValue().set(key, serializableMessages, DEFAULT_TTL);
            System.out.println("成功保存 " + serializableMessages.size() + " 条消息到Redis（过滤了SystemMessage）");

        } catch (Exception e) {
            System.err.println("保存消息到Redis失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null || messages == null) {
            return;
        }

        String sessionId = memoryId.toString();
        System.out.println("更新消息，会话ID: " + sessionId + ", 消息数量: " + messages.size());

        // 1. 保存到Redis（主要存储）
        saveToRedis(sessionId, messages);

        // 2. 同步到DashScope（可选，如果需要在DashScope中持久化）
        try {
            syncToDashScope(sessionId, messages);
        } catch (Exception e) {
            // 这里捕获异常但不抛出，因为Redis保存成功就可以
            System.err.println("同步到DashScope失败，但Redis保存成功: " + e.getMessage());
        }
    }

    /**
     * 同步消息到DashScope
     */
    private void syncToDashScope(String sessionId, List<ChatMessage> messages) {
        System.out.println("开始同步消息到DashScope，会话ID: " + sessionId);

        for (ChatMessage message : messages) {
            try {
                // 跳过 SystemMessage，因为 DashScope 可能不支持
                if (message instanceof SystemMessage) {
                    System.out.println("跳过 SystemMessage: " + aiTypeConversion.extractTextFromMessage(message).substring(0, Math.min(50, aiTypeConversion.extractTextFromMessage(message).length())) + "...");
                    continue;
                }

                // 将ChatMessage转换为DashScope格式
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("content",aiTypeConversion.extractTextFromMessage(message));

                // 确定角色
                String role;
                if (message instanceof UserMessage) {
                    role = "user";
                } else if (message instanceof AiMessage) {
                    role = "assistant";
                } else {
                    System.err.println("未知的消息类型: " + message.type());
                    continue;  // 跳过未知类型
                }
                requestBody.put("role", role);

                // 发送到DashScope
                String url = DASHSCOPE_BASE_URL + "/sessions/{sessionId}/messages";

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        url,
                        request,
                        Map.class,
                        sessionId
                );

                if (response.getStatusCode() != HttpStatus.CREATED) {
                    System.err.println("同步消息到DashScope失败: " + response.getStatusCode());
                } else {
                    System.out.println("成功同步消息到DashScope: " + role + " - " +
                            aiTypeConversion.extractTextFromMessage(message).substring(0, Math.min(50, aiTypeConversion.extractTextFromMessage(message).length())) + "...");
                }

            } catch (Exception e) {
                System.err.println("同步单条消息到DashScope失败: " + e.getMessage());
                // 继续同步下一条消息
            }
        }

        System.out.println("完成同步消息到DashScope");
    }
    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId == null) {
            return;
        }

        String sessionId = memoryId.toString();

        // 1. 从Redis删除
        String key = CHAT_MESSAGES_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        System.out.println("从Redis删除会话: " + sessionId);

        // 2. 从DashScope删除（可选）
        try {
            deleteFromDashScope(sessionId);
        } catch (Exception e) {
            System.err.println("从DashScope删除会话失败: " + e.getMessage());
        }
    }

    /**
     * 从DashScope删除会话
     */
    private void deleteFromDashScope(String sessionId) {
        String url = DASHSCOPE_BASE_URL + "/sessions/{sessionId}";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                Void.class,
                sessionId
        );

        System.out.println("从DashScope删除会话: " + sessionId);
    }

    public boolean exists(String memoryId) {
        if (memoryId == null) {
            return false;
        }

        String key = CHAT_MESSAGES_KEY_PREFIX + memoryId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }


}