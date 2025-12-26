package org.example.backend.dto;

import org.example.backend.model.User;
import org.example.backend.model.UserReport;

import java.io.Serializable;
import java.time.LocalDateTime;

public record UserReportDTO(
        Long id,
        Long reporterId,
        String reporterUsername,
        Long reportedUserId,
        String reportedUsername,
        LocalDateTime reportTime, // 举报时间字段
        String reportReason,
        boolean isProcessed,
        UserReport.ProcessingResult result // 新增属性

) implements Serializable {
    public static UserReportDTO fromUserReport(UserReport userReport, User reporter, User reportedUser) {
        return new UserReportDTO(
                userReport.getId(),
                userReport.getReporterId(),
                reporter != null ? reporter.getUsername() : null,
                userReport.getReportedUserId(),
                reportedUser != null ? reportedUser.getUsername() : null,
                userReport.getReportTime(), // 设置举报时间
                userReport.getReportReason(),
                userReport.isProcessed(),
                userReport.getResult() // 设置处理结果
        );
    }
}