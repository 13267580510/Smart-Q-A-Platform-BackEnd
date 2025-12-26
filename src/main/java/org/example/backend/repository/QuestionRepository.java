// QuestionRepository.java
package org.example.backend.repository;

import org.example.backend.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    Page<Question> findByAuthorIdAndStatus(Long authorId, String status, Pageable pageable);
    Page<Question> findByAuthorId(Long authorId, Pageable pageable);
    Optional<Question> findById(Long questionId);
    @Query("SELECT q FROM Question q " +
            "WHERE q.status = 'NORMAL' " +
            "AND (LOWER(q.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(q.content.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(q.author.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Question> searchQuestions(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT q FROM Question q " +
            "WHERE q.author.id = :authorId " +
            "AND (LOWER(q.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(q.content.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Question> findByAuthorIdAndKeyword(@Param("authorId") Long authorId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT q FROM Question q " +
            "WHERE q.author.id = :authorId AND q.status = :status " +
            "AND (LOWER(q.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(q.content.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Question> findByAuthorIdAndStatusAndKeyword(@Param("authorId") Long authorId, @Param("status") String status, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE " +
            "(:status IS NULL OR q.status = :status) AND " +
            "(:isReported IS NULL OR (q.reportCount > 0 AND :isReported = true) OR (q.reportCount = 0 AND :isReported = false)) AND " +
            "(:authorId IS NULL OR q.author.id = :authorId) AND " +
            "(:title IS NULL OR q.title LIKE %:title%) AND " +
            "(:content IS NULL OR q.content.content LIKE %:content%)")
    List<Question> findQuestions(
            @Param("status") Question.QuestionStatus status,
            @Param("isReported") Boolean isReported,
            @Param("authorId") Long authorId,
            @Param("title") String title,
            @Param("content") String content
    );

    // 新增支持分页的查询方法
    @Query("SELECT q FROM Question q WHERE " +
            "(:status IS NULL OR q.status = :status) AND " +
            "(:isReported IS NULL OR (q.reportCount > 0 AND :isReported = true) OR (q.reportCount = 0 AND :isReported = false)) AND " +
            "(:authorId IS NULL OR q.author.id = :authorId) AND " +
            "(:title IS NULL OR q.title LIKE %:title%) AND " +
            "(:content IS NULL OR q.content.content LIKE %:content%)")
    Page<Question> findQuestionsWithPagination(
            @Param("status") Question.QuestionStatus status,
            @Param("isReported") Boolean isReported,
            @Param("authorId") Long authorId,
            @Param("title") String title,
            @Param("content") String content,
            Pageable pageable
    );
    Integer countByAuthor_Id(Long userId);

}