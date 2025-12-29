package org.example.backend.repository;
import org.example.backend.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findBySessionIdOrderByMessageIndexAsc(String sessionId);

    List<ChatMessageEntity> findBySessionIdAndMessageIndexBetween(String sessionId,
                                                                  int start, int end);
    /**
     * 统计会话消息数量
     */
    @Query("SELECT COUNT(m) FROM ChatMessageEntity m WHERE m.sessionId = :sessionId")
    Integer countMessagesBySessionId(@Param("sessionId") String sessionId);
    /**
     * 批量删除会话消息
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 获取指定范围内的消息
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId AND m.messageIndex BETWEEN :start AND :end ORDER BY m.messageIndex ASC")
    List<ChatMessageEntity> findMessagesByRange(@Param("sessionId") String sessionId,
                                                @Param("start") int start,
                                                @Param("end") int end);
    @Query("SELECT MAX(m.messageIndex) FROM ChatMessageEntity m WHERE m.sessionId = :sessionId")
    Integer findMaxMessageIndexBySessionId(@Param("sessionId") String sessionId);
    /**
     * 删除从指定索引开始的所有消息
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.sessionId = :sessionId AND m.messageIndex >= :fromIndex")
    void deleteMessagesFromIndex(@Param("sessionId") String sessionId,
                                 @Param("fromIndex") int fromIndex);

    /**
     * 根据消息类型获取消息
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId AND m.messageType = :messageType ORDER BY m.messageIndex ASC")
    List<ChatMessageEntity> findBySessionIdAndMessageType(@Param("sessionId") String sessionId,
                                                          @Param("messageType") ChatMessageEntity.MessageType messageType);

    /**
     * 获取指定会话的最新N条消息
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId ORDER BY m.messageIndex DESC")
    List<ChatMessageEntity> findLatestMessages(@Param("sessionId") String sessionId,
                                               org.springframework.data.domain.Pageable pageable);

    default List<ChatMessageEntity> findLatestMessages(String sessionId, int limit) {
        return findLatestMessages(sessionId,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
}