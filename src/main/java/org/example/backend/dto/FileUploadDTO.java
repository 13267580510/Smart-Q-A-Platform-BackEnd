// FileUploadDTO.java
package org.example.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class FileUploadDTO {
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotNull(message = "分片总数不能为空")
    private Integer chunkCount;

    @NotNull(message = "分片大小不能为空")
    private Long chunkSize;

    @NotNull(message = "总大小不能为空")
    private Long totalSize;

    private String md5;
}