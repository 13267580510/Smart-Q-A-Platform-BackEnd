package org.example.backend.dto;
import org.example.backend.model.Question;
import org.example.backend.model.QuestionReport;
import org.example.backend.model.QuestionReport.ReportStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public  record AdminQuestionReportDTO(
        Long id,
        Long questionId,
        String questionUsername,
        Long reporterId,
        String reporterUsername,
        String questionTitle,
        String questionContent,
        QuestionReport.ReasonStatus reason,
        String description,
        LocalDateTime reportTime,
        ReportStatus status
)implements Serializable {

    public static AdminQuestionReportDTO fromQuestionReport(QuestionReport report) {
        return new AdminQuestionReportDTO(
                report.getId(),
                report.getQuestion().getId(),
                report.getQuestion().getAuthor().getUsername(),
                report.getReporter().getId(),
                report.getReporter().getUsername(),
                report.getQuestion().getTitle(),
                report.getQuestion().getContent().getContent(),
                report.getReason(),
                report.getDescription(),
                report.getReportTime(),
                report.getStatus()
        );
    }
}