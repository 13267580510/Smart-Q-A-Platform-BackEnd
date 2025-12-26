package org.example.backend.dto;

import org.example.backend.model.Question;
import org.example.backend.model.QuestionReport;
import org.example.backend.model.User;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record AdminQuestionListDTO(
        Long id,
        String title,
        QuestionContentDTO content,
        AuthorDTO author,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        int viewCount,
        String status,
        Long categoryId,
        Integer reportCount,
        Integer likeCount,
        Integer dislikeCount
)implements Serializable {
    public static AdminQuestionListDTO fromQuestion(Question question) {
        User authorUser = question.getAuthor();
        return new AdminQuestionListDTO(
                question.getId(),
                question.getTitle(),
                new QuestionContentDTO(
                        question.getContent().getId(),
                        question.getContent().getContent()
                ),
                new AuthorDTO(
                        authorUser.getId(),
                        authorUser.getUsername(),
                        authorUser.getNickname(),
                        authorUser.getStatus()
                ),
                question.getCreatedTime(),
                question.getUpdatedTime(),
                question.getViewCount(),
                question.getStatus().toString(),
                question.getCategoryId(),
                question.getReportCount(),
                question.getLikeCount(),
                question.getDislikeCount()
        );
    }



    public record QuestionContentDTO(Long id, String content) {}

    public record AuthorDTO(
            Long id,
            String username,
            String nickname,
            String status
    ) {}

    public record AuthorityDTO(String authority) {}
}