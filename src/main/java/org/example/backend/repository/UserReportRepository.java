package org.example.backend.repository;

import org.example.backend.dto.UserReportDTO;
import org.example.backend.model.QuestionReport;
import org.example.backend.model.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    @Query("SELECT ur FROM UserReport ur " +
            "JOIN User r ON ur.reporterId = r.id " +
            "JOIN User ru ON ur.reportedUserId = ru.id " +
            "WHERE (:reporterUsername IS NULL OR r.username = :reporterUsername) " +
            "AND (:reportedUsername IS NULL OR ru.username = :reportedUsername) " +
            "AND (:reporterNickname IS NULL OR r.nickname = :reporterNickname) " +
            "AND (:reportedNickname IS NULL OR ru.nickname = :reportedNickname) " +
            "AND (:reportTime IS NULL OR ur.reportTime = :reportTime) " +
            "AND (:isProcessed IS NULL OR ur.isProcessed = :isProcessed) " +
            "AND (:result IS NULL OR ur.result = :result)")
    Page<UserReport> findByFilters(
            @Param("reporterUsername") String reporterUsername,
            @Param("reportedUsername") String reportedUsername,
            @Param("reporterNickname") String reporterNickname,
            @Param("reportedNickname") String reportedNickname,
            @Param("reportTime") LocalDateTime reportTime,
            @Param("isProcessed") Boolean isProcessed,
            @Param("result") UserReport.ProcessingResult result,
            Pageable pageable
    );

    @Query("SELECT ur FROM UserReport ur WHERE ur.reportedUserId = :reportedUserId AND ur.isProcessed = true AND ur.result = :result")
    List<UserReport> findProcessedBanReportsByReportedUserId(
            @Param("reportedUserId") Long reportedUserId,
            @Param("result") UserReport.ProcessingResult result
    );

    @Query("SELECT ur FROM UserReport ur WHERE " +
            "ur.reporterId = :reporterId AND " +
            "ur.reportedUserId = :reportedUserId AND " +
            "ur.isProcessed = false")
    Optional<UserReport> findPendingReport(
            @Param("reporterId") Long reporterId,
            @Param("reportedUserId") Long reportedUserId
    );



}