package org.example.backend.model;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_upload_log")
@Data
public class FileUploadLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileKey;         // 文件唯一标识
    private Long userId;            // 用户ID
    private String userIp;          // 用户IP地址

    private String uploadAction;    // 上传动作：init, chunk, merge, download
    private Integer chunkIndex;     // 分片索引

    private String uploadStatus;    // 上传状态：success, failed

    @Column(columnDefinition = "TEXT")
    private String errorMessage;    // 错误信息

    @Column(columnDefinition = "TEXT")
    private String userAgent;       // 用户代理

    private String referer;         // 来源页面

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}