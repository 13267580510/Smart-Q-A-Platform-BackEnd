package org.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.Question;

import java.time.LocalDateTime;

public record AdminAnswerReportDTO(
        Long id,
        Long userId,
        Long answerId,
        String reason,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdTime,
        AnswerReport.ReportStatus status,
        Long answerAuthorId,
        Long questionId,
        String questionTitle,
        String answerContent
) {
    // 静态工厂方法
    public static AdminAnswerReportDTO fromAnswerReport(AnswerReport answerReport, Question question, String answerContent) {
        return new AdminAnswerReportDTO(
                answerReport.getId(),
                answerReport.getUserId(),
                answerReport.getAnswerId(),
                answerReport.getReason(),
                answerReport.getCreatedTime(),
                answerReport.getStatus(),
                answerReport.getAnswerAuthorId(),
                answerReport.getQuestionId(),
                question.getTitle(),
                answerContent
        );
    }
}