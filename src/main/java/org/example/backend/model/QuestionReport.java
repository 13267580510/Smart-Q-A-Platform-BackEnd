// QuestionReport.java
package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "question_report")
public class QuestionReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    @JsonIgnore
    private User reporter;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private  ReasonStatus reason ;//SPAM(垃圾内容)，OFFENSIVE(冒犯性内容)，INAPPROPRIATE(不适当内容)，OTHER(其他)
    public enum ReasonStatus {
        SPAM, OFFENSIVE, INAPPROPRIATE,OTHER
    }
    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "report_time")
    private LocalDateTime reportTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.PENDING;
    public enum ReportStatus {
        PENDING, APPROVED, REJECTED
    }
    public QuestionReport() {}
}