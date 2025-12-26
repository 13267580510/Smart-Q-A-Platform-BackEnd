package org.example.backend.repository;

import org.example.backend.model.Answer;
import org.example.backend.model.AnswerComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerCommentRepository extends JpaRepository<AnswerComment, Long> {
    List<AnswerComment> findByAnswerId(Long answerId);
    List<AnswerComment> findByParentCommentId(Long parentCommentId);
    List<AnswerComment> findByAnswerIdAndParentCommentIdIsNull(Long answerId); // 查询回答的一级评论
    // 分页查询用户评论（需要添加）
    Page<AnswerComment> findByUserId(Long userId, Pageable pageable);
    List<AnswerComment> findByUserId(Long userId);

}