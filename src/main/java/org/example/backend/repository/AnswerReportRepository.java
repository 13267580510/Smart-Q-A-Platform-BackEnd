// AnswerReportRepository.java
package org.example.backend.repository;

import org.example.backend.model.AnswerReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnswerReportRepository extends JpaRepository<AnswerReport, Long> {
    // 分页查询所有举报记录
    Page<AnswerReport> findAll(Pageable pageable);
    Optional<AnswerReport> findByUserIdAndAnswerIdAndStatus(
            Long userId,
            Long answerId,
            AnswerReport.ReportStatus status
    );

    Page<AnswerReport> findByStatus(AnswerReport.ReportStatus status, Pageable pageable);
}