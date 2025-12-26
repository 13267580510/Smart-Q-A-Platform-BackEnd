// UserReport.java
package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_report")
public class UserReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id")
    private Long reporterId;

    @Column(name = "reported_user_id")
    private Long reportedUserId;

    @Column(name = "report_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportTime;

    @Column(name = "report_reason", columnDefinition = "TEXT")
    private String reportReason;

    @Column(name = "is_processed")
    private boolean isProcessed; // 只保留该属性表示是否已处理

    @Column(name = "result")
    @Enumerated(EnumType.STRING)
    private ProcessingResult result;

    public enum ProcessingResult {
        BAN_USER,
        REJECT_REPORT
    }
    public UserReport() {}
}