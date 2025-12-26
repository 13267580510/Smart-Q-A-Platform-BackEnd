package org.example.backend.service;

import org.example.backend.model.AnswerComment;
import org.example.backend.repository.AnswerCommentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnswerCommentService {

    private final AnswerCommentRepository answerCommentRepository;

    public AnswerCommentService(AnswerCommentRepository answerCommentRepository) {
        this.answerCommentRepository = answerCommentRepository;
    }

    public AnswerComment saveComment(Long answerId, Long userId, String content, Long parentCommentId) {
        AnswerComment comment = new AnswerComment();
        comment.setAnswerId(answerId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setParentCommentId(parentCommentId);
        return answerCommentRepository.save(comment);
    }

    public List<AnswerComment> getCommentsByAnswerId(Long answerId) {
        return answerCommentRepository.findByAnswerId(answerId);
    }

    public List<AnswerComment> getCommentsByParentCommentId(Long parentCommentId) {
        return answerCommentRepository.findByParentCommentId(parentCommentId);
    }
}