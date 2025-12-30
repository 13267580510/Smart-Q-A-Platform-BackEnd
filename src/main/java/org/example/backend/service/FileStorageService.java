package org.example.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.FileUploadConfig;
import org.example.backend.dto.ChunkUploadDTO;
import org.example.backend.dto.FileInfoDTO;
import org.example.backend.dto.FileUploadDTO;
import org.example.backend.model.FileInfo;
import org.example.backend.model.ResourceCategory; // 新增：资源分类实体类
import org.example.backend.model.User;
import org.example.backend.repository.FileInfoRepository;
import org.example.backend.repository.ResourceCategoryRepository; // 新增：资源分类Repository
import org.example.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileUploadConfig config;
    private final FileInfoRepository fileRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    // 新增：注入分类Repository
    private final ResourceCategoryRepository categoryRepository;

    /**
     * 从数据库查询所有分类（按创建时间降序）
     * @return 分类名称列表
     */
    public List<String> getValidCategories() {
        return categoryRepository.findAllByOrderByCreateTimeDesc()
                .stream()
                .map(ResourceCategory::getCategoryName)
                .collect(Collectors.toList());
    }

    /**
     * 验证分类是否有效（根据数据库中是否存在该分类名称）
     * @param category 分类名称
     * @return 有效返回true，无效抛出异常
     */
    private boolean validateCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        boolean exists = categoryRepository.existsByCategoryName(category);
        if (!exists) {
            throw new IllegalArgumentException("无效的文件分类：" + category);
        }
        return true;
    }



    /**
     * 检查分片是否存在
     */
    public boolean checkChunkExists(Long fileId, int chunkIndex) {
        String chunkKey = getChunkKey(fileId, chunkIndex);
        return Files.exists(getChunkPath(fileId, chunkIndex));
    }




    /**
     * 初始化上传（只有ADMIN可以调用）
     */
    @Transactional
    public String initUpload(FileUploadDTO dto, String clientIp) {

        try {
            // 优化：使用数据库动态校验分类
            validateCategory(dto.getCategory());

            // 检查文件大小
            if (dto.getTotalSize() > config.getMaxFileSize()) {
                throw new IllegalArgumentException("文件大小超过限制");
            }

            // 创建文件记录
            String fileKey = generateFileKey();
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(dto.getFileName());
            fileInfo.setCategory(dto.getCategory());
            fileInfo.setChunkCount(dto.getChunkCount());
            fileInfo.setChunkSize(dto.getChunkSize().intValue());
            fileInfo.setFileSize(dto.getTotalSize());
            fileInfo.setMd5(dto.getMd5());
            //fileInfo.setUploader(currentUser);
            fileInfo.setUploadIp(clientIp);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileRepository.save(fileInfo);
            // 初始化分片状态
            if (dto.getChunkCount() > 1) {
                initChunkStatus(fileKey, dto.getChunkCount());
            }

            return fileKey;

        } catch (Exception e) {
            log.error("初始化上传失败", e);
            throw new RuntimeException("初始化上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传分片（只有ADMIN可以调用）
     */
//    @Transactional
//    public Map<String, Object> uploadChunk(ChunkUploadDTO dto, String clientIp) {
//        //验证权限
//        //        validateAdminPermission();
//
//        try {
//            // 优化：使用数据库动态校验分类
//            validateCategory(dto.getCategory());
//
//            String fileKey = dto.getFileKey();
//            int chunkIndex = dto.getChunkIndex();
//
//            // 检查文件记录是否存在
//            FileInfo fileInfo = fileRepository.findByFileKey(fileKey)
//                    .orElseThrow(() -> new RuntimeException("文件记录不存在"));
//
//           // User currentUser = getCurrentUser();
////            if (!fileInfo.getUploader().getId().equals(currentUser.getId())) {
////                throw new AccessDeniedException("无权操作此文件");
////            }
//
//            // 检查并发控制
//            if (!acquireUploadLock(fileKey)) {
//                throw new RuntimeException("上传并发数已达上限");
//            }
//
//            try {
//                // 保存分片文件
//                saveChunkFile(dto.getChunk(), fileKey, chunkIndex);
//
//                // 更新分片状态
//                updateChunkStatus(fileKey, chunkIndex);
//
//                // 检查是否所有分片都已完成
//                boolean allChunksUploaded = checkAllChunksUploaded(fileKey, dto.getChunkCount());
//
//                Map<String, Object> result = new HashMap<>();
//                result.put("success", true);
//                result.put("chunkIndex", chunkIndex);
//                result.put("allChunksUploaded", allChunksUploaded);
//                result.put("message", "分片上传成功");
//
//                return result;
//
//            } finally {
//                releaseUploadLock(fileKey);
//            }
//
//        } catch (Exception e) {
//            log.error("分片上传失败", e);
//            throw new RuntimeException("分片上传失败: " + e.getMessage());
//        }
//    }

    /**
     * 合并分片（只有ADMIN可以调用）
     */
    @Transactional
    public Map<String, Object> mergeChunks(Long fileId) {
       // validateAdminPermission();

        try {
            FileInfo fileInfo = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件记录不存在"));

            // 验证文件所有权
            //User currentUser = getCurrentUser();
//            if (!fileInfo.getUploader().getId().equals(currentUser.getId())) {
//                throw new AccessDeniedException("无权操作此文件");
//            }

            // 获取分片状态
            Map<Integer, Boolean> chunkStatus = getChunkStatus(fileId);
            int totalChunks = fileInfo.getChunkCount();

            // 验证所有分片是否已上传
            for (int i = 0; i < totalChunks; i++) {
                if (!chunkStatus.getOrDefault(i, false)) {
                    throw new RuntimeException("分片" + i + "未上传");
                }
            }

            // 创建目标文件路径（基于分类动态创建目录）
            String category = fileInfo.getCategory();
            String fileName = fileInfo.getFileName();
            Path targetPath = getTargetFilePath(category, fileName, fileId);
            Files.createDirectories(targetPath.getParent());

            // 合并分片
            try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkPath = getChunkPath(fileId, i);
                    Files.copy(chunkPath, outputStream);

                    // 删除已合并的分片
                    Files.deleteIfExists(chunkPath);
                }
            }

            // 计算文件MD5
            String fileMd5 = calculateFileMd5(targetPath);

            // 更新文件信息
            fileInfo.setFilePath(targetPath.toString());
            fileInfo.setFileSize(Files.size(targetPath));
            fileInfo.setMd5(fileMd5);
            fileRepository.save(fileInfo);

            // 清理Redis中的分片状态
            cleanUpChunkStatus(fileId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            result.put("fileName", fileName);
            result.put("filePath", targetPath.toString());
            result.put("fileSize", Files.size(targetPath));
            result.put("message", "文件合并完成");

            return result;

        } catch (Exception e) {
            log.error("合并分片失败", e);
            throw new RuntimeException("合并分片失败: " + e.getMessage());
        }
    }


    /**
     * 获取分页文件列表
     */
    public Page<FileInfoDTO> getFilePage(String category, Pageable pageable) {
        Page<FileInfo> filePage;

        if (!Objects.equals(category, "all") && !category.trim().isEmpty()) {
            // 优化：校验分类有效性
            validateCategory(category);
            filePage = fileRepository.findByCategory(category, pageable);
        } else {
            filePage = fileRepository.findAll(pageable);
        }

        return filePage.map(this::convertToDTO);
    }

    /**
     * 搜索文件（USER和ADMIN都可以访问）
     */
    public List<FileInfoDTO> searchFiles(String keyword) {
        List<FileInfo> files = fileRepository.searchFiles(keyword);
        return files.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取文件详情（USER和ADMIN都可以访问）
     */
    public FileInfoDTO getFileDetail(Long fileId) {
        FileInfo fileInfo = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        return convertToDTO(fileInfo);
    }

    /**
     * 下载文件（USER和ADMIN都可以访问）
     */
    public Path downloadFile(Long fileId) {
        FileInfo fileInfo = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));


        Path filePath = Paths.get(fileInfo.getFilePath());
        if (!Files.exists(filePath)) {
            throw new RuntimeException("文件不存在");
        }

        return filePath;
    }

    /**
     * 获取ADMIN上传的文件列表（只有ADMIN可以访问）
     */
//    public List<FileInfoDTO> getMyUploads() {
//        User currentUser = getCurrentUser();
//
//        List<FileInfo> files = fileRepository.findByUploader(currentUser);
//        return files.stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }

//    /**
//     * ADMIN删除自己上传的文件（只有ADMIN可以访问）
//     */
//    @Transactional
//    public void deleteMyFile(String fileKey) {
//        User currentUser = getCurrentUser();
//
//        FileInfo fileInfo = fileRepository.findByFileKey(fileKey)
//                .orElseThrow(() -> new RuntimeException("文件不存在"));
//
//        // 验证文件所有权
//        if (!fileInfo.getUploader().getId().equals(currentUser.getId())) {
//            throw new AccessDeniedException("无权删除此文件");
//        }
//
//        try {
//            // 删除物理文件
//            if (fileInfo.getFilePath() != null) {
//                Path filePath = Paths.get(fileInfo.getFilePath());
//                Files.deleteIfExists(filePath);
//            }
//
//            // 删除分片文件（如果存在）
//            cleanupChunkFiles(fileKey);
//
//            // 删除数据库记录
//            fileRepository.delete(fileInfo);
//
//        } catch (IOException e) {
//            log.error("删除文件失败", e);
//            throw new RuntimeException("删除文件失败");
//        }
//    }

    /**
     * 获取所有分类（USER和ADMIN都可以访问）
     * 兼容原有接口，返回分类名称列表
     */
    public List<String> getCategories() {
        return getValidCategories();
    }

//    /**
//     * 获取文件统计信息（只有ADMIN可以访问）
//     */
//    public Map<String, Object> getFileStats() {
//        validateAdminPermission();
//
//        Map<String, Object> stats = new HashMap<>();
//
//        // 总文件数
//        long totalFiles = fileRepository.count();
//        stats.put("totalFiles", totalFiles);
//
//        // 已完成文件数
//        List<FileInfo> completedFiles = fileRepository.findByUploadStatus("completed");
//        stats.put("completedFiles", completedFiles.size());
//
//        // 总文件大小
//        long totalSize = completedFiles.stream()
//                .mapToLong(FileInfo::getFileSize)
//                .sum();
//        stats.put("totalSize", totalSize);
//        stats.put("totalSizeFormatted", formatFileSize(totalSize));
//
//        // 按分类统计（优化：从数据库查询所有有效分类）
//        Map<String, Map<String, Object>> categoryStats = new HashMap<>();
//        List<String> validCategories = getValidCategories();
//        for (String category : validCategories) {
//            List<FileInfo> categoryFiles = fileRepository.findByCategory(category);
//            long categorySize = categoryFiles.stream()
//                    .mapToLong(FileInfo::getFileSize)
//                    .sum();
//
//            Map<String, Object> categoryStat = new HashMap<>();
//            categoryStat.put("fileCount", categoryFiles.size());
//            categoryStat.put("totalSize", categorySize);
//            categoryStat.put("totalSizeFormatted", formatFileSize(categorySize));
//
//            categoryStats.put(category, categoryStat);
//        }
//        stats.put("categoryStats", categoryStats);
//
//        // 用户上传统计（只统计ADMIN）
//        List<User> admins = userRepository.findByRole(User.UserRole.ADMIN);
//        List<Map<String, Object>> adminStats = new ArrayList<>();
//
//        for (User admin : admins) {
//            List<FileInfo> adminFiles = fileRepository.findByUploader(admin);
//            Map<String, Object> adminStat = new HashMap<>();
//            adminStat.put("userId", admin.getId());
//            adminStat.put("username", admin.getUsername());
//            adminStats.add(adminStat);
//        }
//        stats.put("adminStats", adminStats);
//
//        return stats;
//    }

    // ========== 辅助方法 ==========

    private FileInfoDTO convertToDTO(FileInfo fileInfo) {
        FileInfoDTO dto = new FileInfoDTO();
        dto.setId(fileInfo.getId());
        dto.setFileName(fileInfo.getFileName());
        dto.setFilePath(fileInfo.getFilePath());
        dto.setCategory(fileInfo.getCategory());
        dto.setFileSize(fileInfo.getFileSize());
        dto.setUserId(fileInfo.getUploader().getId());
        dto.setCreateTime(fileInfo.getCreateTime());
        return dto;
    }

    private void saveChunkFile(MultipartFile chunk, Long fileId, int chunkIndex) throws IOException {
        Path chunkPath = getChunkPath(fileId, chunkIndex);
        Files.createDirectories(chunkPath.getParent());

        try (InputStream inputStream = chunk.getInputStream();
             OutputStream outputStream = Files.newOutputStream(chunkPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private Path getChunkPath(Long fileId, int chunkIndex) {
        return config.getChunkTempPath().resolve(String.valueOf(fileId)).resolve(chunkIndex + ".chunk");
    }

    private String getChunkKey(Long fileId, int chunkIndex) {
        return fileId + "_" + chunkIndex;
    }

    private Path getTargetFilePath(String category, String fileName, Long fileId) {
        // 优化：文件名过滤特殊字符，避免路径异常
        String safeFileName = fileId + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return config.getRootPath().resolve(category).resolve(safeFileName);
    }

    private String generateFileKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "unknown";
    }

    private String calculateFileMd5(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return DigestUtils.md5DigestAsHex(inputStream);
        }
    }

    private void initChunkStatus(String fileKey, int chunkCount) {
        String redisKey = "upload:chunk:" + fileKey;
        Map<String, Boolean> status = new HashMap<>();
        for (int i = 0; i < chunkCount; i++) {
            status.put(String.valueOf(i), false);
        }
        redisTemplate.opsForValue().set(redisKey, status, 24, TimeUnit.HOURS);
    }

    private void updateChunkStatus(String fileKey, int chunkIndex) {
        String redisKey = "upload:chunk:" + fileKey;
        @SuppressWarnings("unchecked")
        Map<String, Boolean> status = (Map<String, Boolean>) redisTemplate.opsForValue().get(redisKey);
        if (status != null) {
            status.put(String.valueOf(chunkIndex), true);
            redisTemplate.opsForValue().set(redisKey, status, 24, TimeUnit.HOURS);
        } else {
            throw new RuntimeException("分片状态初始化失败");
        }
    }

    private Map<Integer, Boolean> getChunkStatus(Long fileId) {
        String redisKey = "upload:chunk:" + fileId;
        @SuppressWarnings("unchecked")
        Map<String, Boolean> status = (Map<String, Boolean>) redisTemplate.opsForValue().get(redisKey);
        Map<Integer, Boolean> result = new HashMap<>();
        if (status != null) {
            status.forEach((k, v) -> result.put(Integer.parseInt(k), v));
        }
        return result;
    }

    private boolean checkAllChunksUploaded(Long fileId, int totalChunks) {
        Map<Integer, Boolean> status = getChunkStatus(fileId);
        if (status.size() != totalChunks) {
            return false;
        }
        return status.values().stream().allMatch(Boolean::booleanValue);
    }

    private void cleanUpChunkStatus(Long fileId) {
        String redisKey = "upload:chunk:" + fileId;
        redisTemplate.delete(redisKey);
    }

    private boolean acquireUploadLock(String fileKey) {
        String lockKey = "upload:lock:" + fileKey;
        Long count = redisTemplate.opsForValue().increment(lockKey);
        if (count == 1) {
            redisTemplate.expire(lockKey, 10, TimeUnit.MINUTES);
        }
        return count != null && count <= config.getMaxConcurrent();
    }

    private void releaseUploadLock(String fileKey) {
        String lockKey = "upload:lock:" + fileKey;
        redisTemplate.opsForValue().decrement(lockKey);
    }

    private void cleanupChunkFiles(String fileKey) {
        try {
            Path chunkDir = config.getChunkTempPath().resolve(fileKey);
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
            }
        } catch (Exception e) {
            log.error("清理分片文件失败: {}", fileKey, e);
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}