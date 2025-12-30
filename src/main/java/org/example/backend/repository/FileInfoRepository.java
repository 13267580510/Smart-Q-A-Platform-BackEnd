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
    // 按分类查询已完成的文件
    Page<FileInfo> findByCategory(String category, Pageable pageable);
    // 搜索文件
    @Query("SELECT f FROM FileInfo f WHERE f.fileName LIKE %:keyword% ")
    List<FileInfo> searchFiles(@Param("keyword") String keyword);

    // 获取按分类和状态排序的文件列表
}