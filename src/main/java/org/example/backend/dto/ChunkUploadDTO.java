// ChunkUploadDTO.java
package org.example.backend.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ChunkUploadDTO {
    private String fileKey;
    private String fileName;
    private String category;
    private Integer chunkIndex;
    private Integer chunkCount;
    private String md5;
    private MultipartFile chunk;
}