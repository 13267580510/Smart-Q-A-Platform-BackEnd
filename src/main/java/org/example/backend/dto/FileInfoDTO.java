// FileInfoDTO.java
package org.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileInfoDTO {
    private Long id;
    private String fileName;
    private String fileKey;
    private String filePath;
    private String fileType;
    private String category;
    private Long fileSize;
    private String uploadStatus;
    private Long userId;
    private String username;
    private LocalDateTime createTime;
}