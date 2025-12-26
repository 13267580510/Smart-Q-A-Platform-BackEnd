package org.example.backend.dto;

import org.example.backend.model.Question;
import org.example.backend.model.QuestionImage;
import org.example.backend.model.User;
import org.example.backend.service.QuestionImageService;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record QuestionResponseDTO (
        Long id,
        String title,
        String content,
        LocalDateTime createdTime, // 添加创建时间字段
        int viewCount,
        int answerCount,
        AuthorDTO author,
        int likeCount, // 点赞数
        int dislikeCount, // 点踩数
        Long categoryId,
        String coverImagePath,// 新增封面图片路径字段
        Long solvedAnswerId // 新增解决答案的 ID

) implements Serializable {
    public static QuestionResponseDTO fromQuestion(Question question, QuestionImageService questionImageService) {
        String content = question.getContent() != null ? question.getContent().getContent() : "";
        String truncatedContent = content.length() > 20 ? content.substring(0, 20) + "......" : content;
        User authorUser = question.getAuthor();

        // 获取问题的第一个图片路径
        String coverImagePath = "";
        List<QuestionImage> images = questionImageService.getImagesByQuestionId(question.getId());
        if (!images.isEmpty()) {
            coverImagePath = images.get(0).getImagePath();
        }

        Long solvedAnswerId = question.getIsSolved() != null ? question.getIsSolved().getId() : null;

        return new QuestionResponseDTO(
                question.getId(),
                question.getTitle(),
                truncatedContent,
                question.getCreatedTime(), // 使用实体中的创建时间字段
                question.getViewCount(),//浏览数
                // 修正：回答数应为问题的回答数，而非作者的回答总数
                question.getAnswers() != null ? question.getAnswers().size() : 0,
                new AuthorDTO(
                        authorUser != null ? authorUser.getId() : null,
                        authorUser != null ? authorUser.getNickname() : null
                ),
                question.getLikeCount(), // 设置点赞数
                question.getDislikeCount(), // 设置点踩数
                question.getCategoryId(),
                coverImagePath,  // 新增封面图片路径字段
                solvedAnswerId // 新增解决答案的 ID
        );
    }

    public record AuthorDTO(Long id, String nickname) {}
}