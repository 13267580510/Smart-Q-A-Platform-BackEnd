package org.example.backend.repository;

import org.example.backend.model.FileInfo;
import org.example.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {
    Optional<FileInfo> findByFileKey(String fileKey);

    Optional<FileInfo> findByMd5(String md5);

    // 返回List的方法
    List<FileInfo> findByCategory(String category);

    List<FileInfo> findByUploadStatus(String uploadStatus);

    List<FileInfo> findByUploader(User uploader);

    List<FileInfo> findByUploaderAndCategory(User uploader, String category);

    // 返回Page的方法
    Page<FileInfo> findByUploader(User uploader, Pageable pageable);

    // 只查询已完成的文件
    List<FileInfo> findByUploadStatusOrderByCreateTimeDesc(String status);

    Page<FileInfo> findByUploadStatus(String status, Pageable pageable);

    // 按分类查询已完成的文件
    List<FileInfo> findByCategoryAndUploadStatus(String category, String status);

    Page<FileInfo> findByCategoryAndUploadStatus(String category, String status, Pageable pageable);

    // 搜索文件
    @Query("SELECT f FROM FileInfo f WHERE f.fileName LIKE %:keyword% AND f.uploadStatus = 'completed'")
    List<FileInfo> searchFiles(@Param("keyword") String keyword);

    // 统计用户上传的文件数
    Long countByUploader(User uploader);

    // 统计用户上传的文件总大小
    @Query("SELECT SUM(f.fileSize) FROM FileInfo f WHERE f.uploader = :uploader AND f.uploadStatus = 'completed'")
    Long sumFileSizeByUploader(@Param("uploader") User uploader);

    // 获取按分类和状态排序的文件列表
    List<FileInfo> findByCategoryAndUploadStatusOrderByCreateTimeDesc(String category, String status);
}