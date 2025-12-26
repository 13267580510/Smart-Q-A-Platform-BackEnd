// QuestionReportRepository.java
package org.example.backend.repository;

import org.example.backend.model.QuestionReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {

    boolean existsByQuestionIdAndReporterIdAndStatus(Long questionId, Long reporterId, QuestionReport.ReportStatus status);

    // 新增方法，用于检查是否存在指定 questionId、reporterId 且状态处于指定集合中的记录
    boolean existsByQuestionIdAndReporterIdAndStatusIn(Long questionId, Long reporterId, List<QuestionReport.ReportStatus> statuses);


    @Query("SELECT qr FROM QuestionReport qr " +
            "WHERE (:status IS NULL OR qr.status = :status) AND " +
            "(:startTime IS NULL OR qr.reportTime >= :startTime) AND " +
            "(:endTime IS NULL OR qr.reportTime <= :endTime) AND " +
            "(:questionId IS NULL OR qr.question.id = :questionId) AND " +
            "(:reporterId IS NULL OR qr.reporter.id = :reporterId)")
    Page<QuestionReport> findQuestionReports(
            @Param("status") QuestionReport.ReportStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("questionId") Long questionId,
            @Param("reporterId") Long reporterId,
            Pageable pageable
    );
}