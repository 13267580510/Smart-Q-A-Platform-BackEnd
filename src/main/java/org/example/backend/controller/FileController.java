package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.dto.ChunkUploadDTO;
import org.example.backend.dto.FileInfoDTO;
import org.example.backend.dto.FileUploadDTO;
import org.example.backend.service.FileStorageService;
import org.example.backend.utils.ApiResponse;
import org.example.backend.utils.IpUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.backend.model.ResponseStatus;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final HttpServletRequest request;

    /**
     * 公共接口：获取所有分类
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse> getCategories() {
        try {
            List<String> categories = fileStorageService.getCategories();
            return ResponseEntity.ok(
                    ApiResponse.success(
                            ResponseStatus.SUCCESS.getCode(),
                            ResponseStatus.SUCCESS.getMessage(),
                            categories
                    )
                    );
        } catch (Exception e) {
            log.error("获取分类失败", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            ResponseStatus.BAD_REQUEST.getCode(),
                            e.getMessage()
                    )
            );
        }
    }

    /**
     * 公共接口：获取文件列表（可分页、可按分类筛选）
     */
    @GetMapping("/list")
    public ResponseEntity<?> getFileList(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createTime") String sort,
            @RequestParam(value = "order", defaultValue = "desc") String order) {

        try {
            Sort.Direction direction = "desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

            Page<FileInfoDTO> filePage = fileStorageService.getFilePage(category, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("content", filePage.getContent());
            response.put("totalElements", filePage.getTotalElements());
            response.put("totalPages", filePage.getTotalPages());
            response.put("pageNumber", filePage.getNumber());
            response.put("pageSize", filePage.getSize());

            return ResponseEntity.ok(successResponse("获取成功", response));
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * 公共接口：搜索文件
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(@RequestParam("keyword") String keyword) {
        try {
            List<FileInfoDTO> files = fileStorageService.searchFiles(keyword);
            return ResponseEntity.ok(successResponse("搜索成功", files));
        } catch (Exception e) {
            log.error("搜索文件失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }
    /**
     * 公共接口：下载文件（USER和ADMIN都可以下载）
     */
    @GetMapping("/download/{fileKey}")
    public void downloadFile(@PathVariable String fileKey, HttpServletResponse response) {
        try {
            Path filePath = fileStorageService.downloadFile(fileKey);
            FileInfoDTO fileInfo = fileStorageService.getFileDetail(fileKey);

            // 设置响应头
            String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encodedFileName);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");

            // 传输文件
            if (Files.exists(filePath)) {
                response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(Files.size(filePath)));

                try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(filePath))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        response.getOutputStream().write(buffer, 0, bytesRead);
                    }
                    response.getOutputStream().flush();
                }
            } else {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                response.getWriter().write("文件不存在");
            }

        } catch (Exception e) {
            log.error("下载文件失败", e);
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.getWriter().write("下载失败: " + e.getMessage());
            } catch (IOException ex) {
                log.error("写入响应失败", ex);
            }
        }
    }

    /**
     * ADMIN接口：检查分片是否存在
     */
    @GetMapping("/admin/check-chunk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> checkChunkExists(
            @RequestParam("fileKey") String fileKey,
            @RequestParam("chunkIndex") Integer chunkIndex) {
        try {
            boolean exists = fileStorageService.checkChunkExists(fileKey, chunkIndex);
            Map<String, Object> result = new HashMap<>();
            result.put("exists", exists);
            result.put("fileKey", fileKey);
            result.put("chunkIndex", chunkIndex);
            return ResponseEntity.ok(successResponse("检查成功", result));
        } catch (Exception e) {
            log.error("检查分片失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：初始化上传
     */
    @PostMapping("/admin/init-upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> initUpload(@RequestBody FileUploadDTO dto) {
        try {
            String clientIp = IpUtil.getClientIp(request);
            String fileKey = fileStorageService.initUpload(dto, clientIp);

            Map<String, Object> result = new HashMap<>();
            result.put("fileKey", fileKey);
            result.put("message", "上传初始化成功");

            return ResponseEntity.ok(successResponse("初始化成功", result));
        } catch (Exception e) {
            log.error("初始化上传失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：上传分片
     */
    @PostMapping("/admin/upload-chunk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("fileKey") String fileKey,
            @RequestParam("fileName") String fileName,
            @RequestParam("category") String category,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunkCount") Integer chunkCount,
            @RequestParam(value = "md5", required = false) String md5,
            @RequestParam("chunk") MultipartFile chunk) {

        try {
            String clientIp = IpUtil.getClientIp(request);

            ChunkUploadDTO dto = new ChunkUploadDTO();
            dto.setFileKey(fileKey);
            dto.setFileName(fileName);
            dto.setCategory(category);
            dto.setChunkIndex(chunkIndex);
            dto.setChunkCount(chunkCount);
            dto.setMd5(md5);
            dto.setChunk(chunk);

            Map<String, Object> result = fileStorageService.uploadChunk(dto, clientIp);
            return ResponseEntity.ok(successResponse("分片上传成功", result));
        } catch (Exception e) {
            log.error("上传分片失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：合并分片
     */
    @PostMapping("/admin/merge-chunks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> mergeChunks(@RequestParam("fileKey") String fileKey) {
        try {
            Map<String, Object> result = fileStorageService.mergeChunks(fileKey);
            return ResponseEntity.ok(successResponse("合并成功", result));
        } catch (Exception e) {
            log.error("合并分片失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：获取我的上传记录
     */
    @GetMapping("/admin/my-uploads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getMyUploads() {
        try {
            List<FileInfoDTO> files = fileStorageService.getMyUploads();
            return ResponseEntity.ok(successResponse("获取成功", files));
        } catch (Exception e) {
            log.error("获取上传记录失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：删除我的文件
     */
    @DeleteMapping("/admin/delete/{fileKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMyFile(@PathVariable String fileKey) {
        try {
            fileStorageService.deleteMyFile(fileKey);
            return ResponseEntity.ok(successResponse("删除成功", null));
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    /**
     * ADMIN接口：获取文件统计
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFileStats() {
        try {
            Map<String, Object> stats = fileStorageService.getFileStats();
            return ResponseEntity.ok(successResponse("获取成功", stats));
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    // 统一响应格式
    private Map<String, Object> successResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}