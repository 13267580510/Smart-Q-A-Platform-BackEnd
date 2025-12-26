package org.example.backend.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "upload")
public class FileUploadConfig {
    private String path;                // 文件存储根路径
    private Long chunkSize;             // 分片大小
    private Long maxFileSize;           // 最大文件大小
    private String chunkTempDir;        // 临时分片目录
    private Integer maxConcurrent;      // 最大并发数
    private Long timeout;               // 超时时间

    public Path getRootPath() {
        return Paths.get(path).toAbsolutePath().normalize();
    }

    public Path getChunkTempPath() {
        return Paths.get(chunkTempDir).toAbsolutePath().normalize();
    }
}