package org.example.backend.service.ai;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.dto.BatchSaveMessagesRequest;
import org.example.backend.dto.ChatMessageDTO;
import org.example.backend.model.ChatMessageEntity;
import org.example.backend.repository.ChatMessageRepository;
import org.example.backend.utils.MessageConverter;
import org.example.backend.service.ai.memory.HybridChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private HybridChatMemoryStore hybridChatMemoryStore;

    /**
     * 批量保存消息（带参数）
     */
    public int saveMessages(String sessionId, List<ChatMessage> messages, boolean skipExisting) {
        System.out.println("开始执行："+"saveMessages（带参数）");

        if (sessionId == null || messages == null || messages.isEmpty()) {
            return 0;
        }

        try {
            // 获取当前最大索引
            Integer maxIndex = chatMessageRepository.findMaxMessageIndexBySessionId(sessionId);
            int startIndex = (maxIndex != null) ? maxIndex + 1 : 0;

            // 如果跳过已存在，需要检查哪些消息已经存在
            List<ChatMessage> messagesToSave = messages;
            if (skipExisting) {
                messagesToSave = filterExistingMessages(sessionId, messages);
                if (messagesToSave.isEmpty()) {
                    log.debug("所有消息已存在，跳过保存: sessionId={}", sessionId);
                    return 0;
                }
            }

            // 准备实体列表
            List<ChatMessageEntity> entities = new ArrayList<>();
            for (int i = 0; i < messagesToSave.size(); i++) {

                ChatMessageEntity entity = messageConverter.toEntity(
                        messagesToSave.get(i),
                        sessionId,
                        startIndex + i
                );
                System.out.println("=== 转换后的实体 ===");
                System.out.println("索引: " + (startIndex + i));
                System.out.println("Entity: " + entity);  // 调用toString()
                System.out.println("Entity JSON: " + JSON.toJSONString(entity));  // 转成JSON格式
                entities.add(entity);
            }

            // 批量保存
            if (!entities.isEmpty()) {
                chatMessageRepository.saveAll(entities);

                // 使用混合存储更新缓存
                List<ChatMessage> allMessages = getAllMessages(sessionId);
                hybridChatMemoryStore.updateMessages(sessionId, allMessages);

                log.info("批量保存消息成功: sessionId={}, count={}", sessionId, entities.size());
                return entities.size();
            }

            return 0;

        } catch (Exception e) {
            log.error("批量保存消息失败: sessionId={}", sessionId, e);
            throw new RuntimeException("批量保存消息失败", e);
        }
    }

    /**
     * 批量保存消息（DTO版本）
     */
    public int saveMessages(BatchSaveMessagesRequest request) {
        System.out.println("开始执行："+"saveMessages（DTO版本）");
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }

        // 使用新的验证方法
        if (!isValidRequest(request)) {
            throw new IllegalArgumentException("请求参数无效");
        }

        try {
            // 验证会话所有权
            if (request.getUserId() != null) {
                validateSessionOwnership(request.getSessionId(), request.getUserId());
            }

            return saveMessages(
                    request.getSessionId(),
                    request.getMessages(),
                    request.getSkipExisting()
            );

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量保存消息失败: sessionId={}", request.getSessionId(), e);
            throw new RuntimeException("保存消息失败", e);
        }
    }


    /**
     * 过滤已存在的消息
     */
    private List<ChatMessage> filterExistingMessages(String sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 获取数据库中已存在的消息内容
            List<ChatMessageEntity> existingEntities = chatMessageRepository
                    .findBySessionIdOrderByMessageIndexAsc(sessionId);

            Set<String> existingContent = existingEntities.stream()
                    .map(ChatMessageEntity::getContent)
                    .collect(Collectors.toSet());

            // 过滤掉内容已存在的消息
            return messages.stream()
                    .filter(msg -> {
                        String content = messageConverter.extractTextFromMessage(msg);
                        return !existingContent.contains(content);
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("过滤已存在消息失败: sessionId={}", sessionId, e);
            return messages; // 出错时保存所有消息
        }
    }

    /**
     * 验证请求是否有效
     */
    private boolean isValidRequest(BatchSaveMessagesRequest request) {
        return request != null
                && request.getSessionId() != null
                && !request.getSessionId().trim().isEmpty()
                && request.getMessages() != null
                && !request.getMessages().isEmpty();
    }



    /**
     * 获取会话的所有消息
     */
    public List<ChatMessage> getAllMessages(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }

        try {
            // 使用混合存储获取消息（会自动处理缓存）
            return hybridChatMemoryStore.getMessages(sessionId);

        } catch (Exception e) {
            log.error("获取消息失败: sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取消息并转换为DTO
     */
    public List<ChatMessageDTO> getMessagesAsDTO(String sessionId) {
        List<ChatMessage> messages = getAllMessages(sessionId);

        return messages.stream()
                .map(this::convertToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 获取消息数量
     */
    public int getMessageCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }

        try {
            Integer count = chatMessageRepository.countMessagesBySessionId(sessionId);
            return count != null ? count : 0;

        } catch (Exception e) {
            log.error("获取消息数量失败: sessionId={}", sessionId, e);
            return 0;
        }
    }

    /**
     * 删除会话的所有消息
     */
    public void deleteAllMessages(String sessionId, Long userId) {
        if (sessionId == null) {
            return;
        }

        try {
            // 验证会话所有权（如果提供了userId）
            if (userId != null) {
                validateSessionOwnership(sessionId, userId);
            }

            // 删除数据库记录
            chatMessageRepository.deleteBySessionId(sessionId);

            // 清理缓存
            hybridChatMemoryStore.deleteMessages(sessionId);

            log.info("删除会话消息: sessionId={}, userId={}", sessionId, userId);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除消息失败: sessionId={}", sessionId, e);
            throw new RuntimeException("删除消息失败", e);
        }
    }

    /**
     * 删除指定索引之后的所有消息（用于撤回）
     */
    public int deleteMessagesFromIndex(String sessionId, int fromIndex, Long userId) {
        if (sessionId == null || fromIndex < 0) {
            return 0;
        }

        try {
            // 验证会话所有权
            if (userId != null) {
                validateSessionOwnership(sessionId, userId);
            }

            // 获取要删除的消息数量
            int totalCount = getMessageCount(sessionId);
            int deleteCount = Math.max(0, totalCount - fromIndex);

            if (deleteCount == 0) {
                return 0;
            }

            // 从指定索引删除
            chatMessageRepository.deleteMessagesFromIndex(sessionId, fromIndex);

            // 重新加载剩余消息并更新缓存
            List<ChatMessage> remainingMessages = getAllMessages(sessionId);
            hybridChatMemoryStore.updateMessages(sessionId, remainingMessages);

            log.info("删除指定索引之后的消息: sessionId={}, fromIndex={}, count={}",
                    sessionId, fromIndex, deleteCount);

            return deleteCount;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除指定索引消息失败: sessionId={}, fromIndex={}", sessionId, fromIndex, e);
            throw new RuntimeException("删除消息失败", e);
        }
    }

    /**
     * 导出会话消息（用于备份）
     */
    public Map<String, Object> exportSessionMessages(String sessionId, Long userId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }

        try {
            // 验证会话所有权
            if (userId != null) {
                validateSessionOwnership(sessionId, userId);
            }

            // 获取所有消息
            List<ChatMessage> messages = getAllMessages(sessionId);
            List<ChatMessageDTO> messageDTOs = getMessagesAsDTO(sessionId);

            // 构建导出数据
            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("sessionId", sessionId);
            exportData.put("exportTime", LocalDateTime.now());
            exportData.put("totalMessages", messages.size());
            exportData.put("messages", messageDTOs);

            // 添加统计信息
            Map<String, Long> typeStats = messages.stream()
                    .collect(Collectors.groupingBy(
                            msg -> msg.type().toString(),
                            Collectors.counting()
                    ));
            exportData.put("statistics", typeStats);

            log.info("导出会话消息: sessionId={}, count={}", sessionId, messages.size());
            return exportData;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出消息失败: sessionId={}", sessionId, e);
            throw new RuntimeException("导出消息失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证会话所有权
     */
    private void validateSessionOwnership(String sessionId, Long userId) {
        // 这里需要实现会话所有权的验证逻辑
        // 可以从数据库查询会话信息，检查userId是否匹配
        // 暂时简单实现，实际使用时需要完善
        if (sessionId == null || userId == null) {
            throw new SecurityException("参数不完整，无法验证所有权");
        }
        log.debug("验证会话所有权: sessionId={}, userId={}", sessionId, userId);
        // TODO: 实现实际的验证逻辑
    }

    /**
     * 转换为DTO
     */
    private ChatMessageDTO convertToDTO(ChatMessage message) {
        if (message == null) {
            return null;
        }

        try {
            String content = messageConverter.extractContent(message);
            String role = getRoleFromMessage(message);

            return ChatMessageDTO.builder()
                    .type(message.type().toString())
                    .role(role)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .metadata(extractMetadata(message))
                    .build();

        } catch (Exception e) {
            log.warn("转换消息为DTO失败: {}", message, e);
            return null;
        }
    }

    /**
     * 从消息中提取角色
     */
    private String getRoleFromMessage(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return "user";
        } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
            return "assistant";
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            return "system";
        }
        return "unknown";
    }

    /**
     * 提取消息元数据 - 修正AiMessage处理
     */
    private Map<String, Object> extractMetadata(ChatMessage message) {
        Map<String, Object> metadata = new HashMap<>();

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            metadata.put("type", "USER");
            if (userMessage.name() != null) {
                metadata.put("name", userMessage.name());
            }
        } else if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            metadata.put("type", "AI");
            metadata.put("hasToolExecution", aiMessage.hasToolExecutionRequests());
            // 修正：不再使用toolExecutionRequest()
            // 如果需要工具执行信息，可以这样获取：
            if (aiMessage.hasToolExecutionRequests()) {
                // 如果有工具执行请求，可以获取详细信息
                metadata.put("toolExecutionCount", aiMessage.toolExecutionRequests().size());
            }
        }

        return metadata;
    }
}