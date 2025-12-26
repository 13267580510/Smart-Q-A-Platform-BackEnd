package org.example.backend.dto;

import lombok.Data;

@Data
public class UploadResultDTO {
    private String fileKey;
    private String fileName;
    private String filePath;
    private String category;
    private Long fileSize;
    private Boolean uploaded;       // 是否已上传
    private Boolean merged;         // 是否已合并
    private String message;
}