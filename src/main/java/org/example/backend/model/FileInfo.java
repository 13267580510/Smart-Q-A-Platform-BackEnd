// FileInfo.java
package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_info")
@Data
public class FileInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;        // 原始文件名
    private String fileKey;         // 文件唯一标识
    private String filePath;        // 存储路径
    private String fileType;        // 文件类型
    private String category;        // 分类：办公软件、开发工具等

    private Long fileSize;          // 文件大小（字节）
    private Integer chunkCount;     // 分片总数
    private Integer chunkSize;      // 分片大小

    private String md5;             // 文件MD5值
    private String uploadStatus;    // 上传状态：uploading, completed, failed

    @Column(columnDefinition = "TEXT")
    private String chunkStatus;     // 分片上传状态（JSON格式）

    // 关联上传用户
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User uploader;

    // 方便获取用户ID
    @Transient
    private Long userId;

    // 方便获取用户名
    @Transient
    private String username;

    private String uploadIp;        // 上传IP地址
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // 获取用户信息的方法
    public Long getUserId() {
        return uploader != null ? uploader.getId() : null;
    }

    public String getUsername() {
        return uploader != null ? uploader.getUsername() : null;
    }
}