// AnswerReport.java
package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "answer_reports")
public class AnswerReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Long userId;


    @Column(name = "answer_id", nullable = false)
    private Long answerId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_time", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;
    public enum ReportStatus {
        PENDING, APPROVED, REJECTED
    }
    @Column(name = "answer_author_id", nullable = false)
    private Long answerAuthorId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;
    public AnswerReport() {}
}