package org.example.backend.repository;


import org.example.backend.model.FileUploadLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileUploadLogRepository extends JpaRepository<FileUploadLog, Long> {

    List<FileUploadLog> findByFileKey(String fileKey);

    List<FileUploadLog> findByUserId(Long userId);

    List<FileUploadLog> findByUserIp(String userIp);

    @Query("SELECT l FROM FileUploadLog l WHERE l.userId = :userId AND l.createTime BETWEEN :startTime AND :endTime")
    List<FileUploadLog> findByUserIdAndTimeRange(@Param("userId") Long userId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(DISTINCT l.userIp) FROM FileUploadLog l WHERE DATE(l.createTime) = CURRENT_DATE")
    Long countDistinctIpToday();

    Page<FileUploadLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}