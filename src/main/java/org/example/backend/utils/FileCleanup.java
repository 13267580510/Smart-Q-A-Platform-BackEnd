package org.example.backend.utils;

// FileCleanupTask.java
import org.example.backend.model.FileInfo;
import org.example.backend.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanup {
    private final FileInfoRepository fileRepository;



    private void cleanupChunkFiles(String fileKey) {
        try {
            // 清理临时分片目录
            Path chunkDir = Paths.get("./uploads/chunks", fileKey);
            if (Files.exists(chunkDir)) {
                Files.walk(chunkDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("删除分片文件失败: {}", path, e);
                            }
                        });
                log.info("清理分片文件: {}", fileKey);
            }
        } catch (Exception e) {
            log.error("清理分片文件失败: {}", fileKey, e);
        }
    }
}