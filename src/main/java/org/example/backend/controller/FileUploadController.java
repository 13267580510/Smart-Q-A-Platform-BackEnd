package org.example.backend.controller;

// FileUploadController.java
import org.example.backend.dto.*;
import org.example.backend.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.utils.IpUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {
    private final FileStorageService fileStorageService;

    /**
     * 初始化上传
     */
    @PostMapping("/init")
    public ResponseEntity<?> initUpload(@RequestBody FileUploadDTO dto) {
        try {
            // 从请求中获取用户ID和IP（实际项目中可能从JWT token获取）
            Long userId = getCurrentUserId();
            String userIp = IpUtil.getClientIp(request);

            dto.setUserId(userId);
            dto.setUserIp(userIp);

            String fileKey = fileStorageService.initUpload(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "初始化成功", fileKey));
        } catch (Exception e) {
            log.error("初始化上传失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * 上传分片
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("fileKey") String fileKey,
            @RequestParam("fileName") String fileName,
            @RequestParam("category") String category,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkCount") Integer chunkCount,
            @RequestParam(value = "md5", required = false) String md5,
            @RequestParam("chunk") MultipartFile chunk) {

        try {
            Long userId = getCurrentUserId();
            String userIp = IpUtil.getClientIp(request);

            ChunkUploadDTO dto = new ChunkUploadDTO();
            dto.setFileKey(fileKey);
            dto.setFileName(fileName);
            dto.setCategory(category);
            dto.setChunkIndex(chunkIndex);
            dto.setChunkCount(chunkCount);
            dto.setMd5(md5);
            dto.setChunk(chunk);
            dto.setUserId(userId);
            dto.setUserIp(userIp);

            UploadResultDTO result = fileStorageService.uploadChunk(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "分片上传成功", result));
        } catch (Exception e) {
            log.error("上传分片失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * 检查分片是否存在
     */
    @GetMapping("/check/chunk")
    public ResponseEntity<?> checkChunk(
            @RequestParam("fileKey") String fileKey,
            @RequestParam("chunkIndex") Integer chunkIndex) {

        try {
            boolean exists = fileStorageService.checkChunkExists(fileKey, chunkIndex);
            return ResponseEntity.ok(new ApiResponse<>(true, "检查成功", exists));
        } catch (Exception e) {
            log.error("检查分片失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }



    /**
     * 合并分片
     */
    @PostMapping("/merge")
    public ResponseEntity<?> mergeChunks(@RequestParam("fileKey") String fileKey) {
        try {
            UploadResultDTO result = fileStorageService.mergeChunks(fileKey);
            return ResponseEntity.ok(new ApiResponse<>(true, "合并成功", result));
        } catch (Exception e) {
            log.error("合并分片失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{fileKey}")
    public void downloadFile(@PathVariable String fileKey, HttpServletResponse response) {
        try {
            FileInfoDTO fileInfo = fileStorageService.getFileInfo(fileKey);

            // 设置响应头
            String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encodedFileName);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");

            // 获取文件路径并传输
            Path filePath = Path.of(fileInfo.getFilePath());
            if (Files.exists(filePath)) {
                response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(Files.size(filePath)));

                try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(filePath))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        response.getOutputStream().write(buffer, 0, bytesRead);
                    }
                    response.getOutputStream().flush();
                }
            } else {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                response.getWriter().write("File not found");
            }

        } catch (Exception e) {
            log.error("下载文件失败", e);
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.getWriter().write("Download failed: " + e.getMessage());
            } catch (IOException ex) {
                log.error("写入响应失败", ex);
            }
        }
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info/{fileKey}")
    public ResponseEntity<?> getFileInfo(@PathVariable String fileKey) {
        try {
            FileInfoDTO fileInfo = fileStorageService.getFileInfo(fileKey);
            return ResponseEntity.ok(new ApiResponse<>(true, "获取成功", fileInfo));
        } catch (Exception e) {
            log.error("获取文件信息失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * 获取分类下的文件列表
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<?> getFilesByCategory(@PathVariable String category) {
        try {
            List<FileInfoDTO> files = fileStorageService.getFilesByCategory(category);
            return ResponseEntity.ok(new ApiResponse<>(true, "获取成功", files));
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<String> categories = List.of(
                "办公软件", "开发工具", "学习资料", "实用脚本", "运维工具", "系统工具"
        );
        return ResponseEntity.ok(new ApiResponse<>(true, "获取成功", categories));
    }

    // 统一响应格式
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
    }
}