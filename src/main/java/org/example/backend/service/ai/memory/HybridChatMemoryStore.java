package org.example.backend.service.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.CacheKeyConfig;
import org.example.backend.model.ChatMessageEntity;
import org.example.backend.repository.ChatMessageRepository;
import org.example.backend.utils.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HybridChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MessageConverter messageConverter;

    // 缓存配置
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int BATCH_SIZE = 50; // 批量操作大小

    /**
     * 获取会话的所有消息（实现ChatMemoryStore接口）
     * 策略：先Redis缓存，没有再MySQL，然后回填Redis
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId == null) {
            log.warn("memoryId为空，返回空列表");
            return Collections.emptyList();
        }

        String sessionId = memoryId.toString();
        log.debug("获取会话消息: sessionId={}", sessionId);

        // 1. 尝试从Redis缓存获取
        List<ChatMessage> cachedMessages = getFromRedis(sessionId);
        if (!cachedMessages.isEmpty()) {
            log.debug("从Redis缓存获取消息成功: sessionId={}, count={}", sessionId, cachedMessages.size());
            return cachedMessages;
        }

        // 2. Redis中没有，从MySQL数据库获取
        log.debug("Redis缓存未命中，从MySQL获取: sessionId={}", sessionId);
        List<ChatMessage> dbMessages = loadFromDatabase(sessionId);

        // 3. 异步保存到Redis缓存（不阻塞当前请求）
        if (!dbMessages.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    saveToRedis(sessionId, dbMessages);
                    log.debug("异步保存消息到Redis: sessionId={}, count={}", sessionId, dbMessages.size());
                } catch (Exception e) {
                    log.error("异步保存Redis缓存失败: sessionId={}", sessionId, e);
                }
            });
        }

        return dbMessages;
    }

    /**
     * 更新/保存会话消息（实现ChatMemoryStore接口）
     * 策略：先保存到MySQL，再更新Redis缓存
     */
    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId == null || messages == null) {
            log.warn("参数为空，跳过更新: memoryId={}, messages={}", memoryId, messages);
            return;
        }

        String sessionId = memoryId.toString();
        log.debug("更新会话消息: sessionId={}, count={}", sessionId, messages.size());

        try {
            System.out.println("==================开始执行保存====================");
            System.out.println("保存历史会话到mysql中,message:"+messages);
            System.out.println("==================执行结束====================");

            // 1. 保存到MySQL数据库（主存储）
            saveToDatabase(sessionId, messages);

            System.out.println("尝试调用redis更新");
            // 2. 异步更新Redis缓存
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("尝试调用redis更新");
                    saveToRedis(sessionId, messages);
                    log.debug("异步更新Redis缓存成功: sessionId={}, count={}", sessionId, messages.size());
                } catch (Exception e) {
                    log.error("异步更新Redis缓存失败: sessionId={}", sessionId, e);
                }
            });

        } catch (Exception e) {
            log.error("更新消息失败: sessionId={}", sessionId, e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    /**
     * 删除会话的所有消息（实现ChatMemoryStore接口）
     */
    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        if (memoryId == null) {
            return;
        }

        String sessionId = memoryId.toString();
        log.info("删除会话消息: sessionId={}", sessionId);

        try {
            // 1. 从MySQL删除
            chatMessageRepository.deleteBySessionId(sessionId);
            log.debug("从MySQL删除消息完成: sessionId={}", sessionId);

            // 2. 从Redis删除缓存
            deleteFromRedis(sessionId);
            log.debug("从Redis删除缓存完成: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("删除消息失败: sessionId={}", sessionId, e);
            throw new RuntimeException("删除消息失败", e);
        }
    }



    /**
     * 从Redis缓存获取消息
     */
    private List<ChatMessage> getFromRedis(String sessionId) {
        String redisKey = CacheKeyConfig.buildMessagesKey(sessionId);

        try {
            Object cachedData = redisTemplate.opsForValue().get(redisKey);
            if (cachedData instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cachedList = (List<Map<String, Object>>) cachedData;

                if (cachedList != null && !cachedList.isEmpty()) {
                    // 延长缓存过期时间
                    redisTemplate.expire(redisKey, CACHE_TTL);

                    // 转换为ChatMessage列表
                    return convertFromCachedFormat(cachedList);
                }
            }
        } catch (Exception e) {
            log.warn("从Redis获取消息失败，继续尝试数据库: sessionId={}", sessionId, e);
        }

        return Collections.emptyList();
    }

    /**
     * 保存消息到Redis缓存
     */
    private void saveToRedis(String sessionId, List<ChatMessage> messages) {
        System.out.println("调用redis更新");
        if (sessionId == null || messages == null || messages.isEmpty()) {
            return;
        }

        String redisKey = CacheKeyConfig.buildMessagesKey(sessionId);

        try {
            // 转换为可序列化的格式
            System.out.println("saveToRedis.messages:"+messages);
            List<Map<String, Object>> cacheData = convertToCachedFormat(messages);
            System.out.println("保存到Redis成功: redisKey={"+redisKey+"}");
            System.out.println("保存到Redis成功: cacheData={"+cacheData+"}");
            redisTemplate.opsForValue().set(redisKey, cacheData, CACHE_TTL);
            System.out.println("保存到Redis成功: sessionId={}"+sessionId);
        } catch (Exception e) {
            log.error("保存消息到Redis失败: sessionId={}", sessionId, e);
            // 这里不抛出异常，因为MySQL保存成功了
        }
    }

    /**
     * 从Redis删除缓存
     */
    private void deleteFromRedis(String sessionId) {
        String redisKey = CacheKeyConfig.buildMessagesKey(sessionId);
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.error("从Redis删除缓存失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 从MySQL数据库加载消息
     */
    private List<ChatMessage> loadFromDatabase(String sessionId) {
        try {
            // 按顺序获取所有消息
            List<ChatMessageEntity> entities = chatMessageRepository
                    .findBySessionIdOrderByMessageIndexAsc(sessionId);

            if (entities.isEmpty()) {
                log.debug("数据库中未找到消息: sessionId={}", sessionId);
                return Collections.emptyList();
            }

            // 转换为ChatMessage列表
            List<ChatMessage> messages = entities.stream()
                    .map(messageConverter::toChatMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("从数据库加载消息成功: sessionId={}, count={}", sessionId, messages.size());
            return messages;

        } catch (Exception e) {
            log.error("从数据库加载消息失败: sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 保存消息到MySQL数据库
     */
    private void saveToDatabase(String sessionId, List<ChatMessage> messages) {
        if (sessionId == null || messages == null || messages.isEmpty()) {
            return;
        }

        try {
            // 获取当前最大消息索引
            Integer maxIndex = chatMessageRepository.findMaxMessageIndexBySessionId(sessionId);
            int startIndex = (maxIndex != null) ? maxIndex + 1 : 0;

            // 准备实体列表
            List<ChatMessageEntity> entities = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessageEntity entity = messageConverter.toEntity(
                        messages.get(i),
                        sessionId,
                        startIndex + i
                );
                if(entity.getRole()=="system"){
                    System.out.println("entity:"+ entity);
                    continue;
                }
                entities.add(entity);
            }

            // 批量保存
            if (!entities.isEmpty()) {
                chatMessageRepository.saveAll(entities);
                log.debug("保存到数据库成功: sessionId={}, count={}", sessionId, entities.size());
            }

        } catch (Exception e) {
            log.error("保存到数据库失败: sessionId={}", sessionId, e);
            throw new RuntimeException("数据库保存失败", e);
        }
    }

    /**
     * 转换为Redis缓存格式
     */
    private List<Map<String, Object>> convertToCachedFormat(List<ChatMessage> messages) {
        List<Map<String, Object>> cacheData = new ArrayList<>();

        for (ChatMessage message : messages) {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("type", message.type().toString());
            messageMap.put("content", messageConverter.extractContent(message));
            System.out.println("content:  "+messageConverter.extractContent(message));
            // 添加角色信息
            if (message instanceof dev.langchain4j.data.message.UserMessage) {
                messageMap.put("role", "user");
            } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
                messageMap.put("role", "assistant");
            } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
                messageMap.put("role", "system");
            }

            cacheData.add(messageMap);
        }

        return cacheData;
    }

    /**
     * 从Redis缓存格式转换
     */
    private List<ChatMessage> convertFromCachedFormat(List<Map<String, Object>> cacheData) {
        List<ChatMessage> messages = new ArrayList<>();

        for (Map<String, Object> messageMap : cacheData) {
            try {
                String type = (String) messageMap.get("type");
                String content = (String) messageMap.get("content");

                if (type != null && content != null) {
                    ChatMessage message = createChatMessage(type, content);
                    if (message != null) {
                        messages.add(message);
                    }
                }
            } catch (Exception e) {
                log.warn("转换缓存消息失败: {}", messageMap, e);
            }
        }

        return messages;
    }

    /**
     * 根据类型创建ChatMessage
     */
    private ChatMessage createChatMessage(String type, String content) {
        switch (type) {
            case "USER":
                return dev.langchain4j.data.message.UserMessage.from(content);
            case "AI":
                return dev.langchain4j.data.message.AiMessage.from(content);
            case "SYSTEM":
                return dev.langchain4j.data.message.SystemMessage.from(content);
            default:
                log.warn("未知的消息类型: {}", type);
                return null;
        }
    }
}