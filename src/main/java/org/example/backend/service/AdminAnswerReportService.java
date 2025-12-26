// AnswerReportService.java
package org.example.backend.service;

import org.example.backend.dto.AdminAnswerReportDTO;
import org.example.backend.model.Answer;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.Notification;
import org.example.backend.model.Question;
import org.example.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.backend.model.AnswerReport.ReportStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminAnswerReportService {
    private final AnswerReportRepository answerReportRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AdminNotificationRepository adminNotificationRepository;
    private AnswerService answerService;

    public AdminAnswerReportService(AnswerReportRepository answerReportRepository,
                               AnswerRepository answerRepository,
                               UserRepository userRepository,
                               QuestionRepository questionRepository,
                               AdminNotificationRepository adminNotificationRepository,
                               AnswerService answerService
                                ) {
        this.answerReportRepository = answerReportRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.adminNotificationRepository = adminNotificationRepository;
        this.answerService = answerService;
    }

    @Transactional
    public void approveReport(Long reportId, boolean isApproved) {
        AnswerReport answerReport = answerReportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("举报记录不存在"));

        if (answerReport.getStatus() == AnswerReport.ReportStatus.PENDING) {
            if (isApproved) {
                // 通过审批，删除回答

                Optional<Answer> reportedAnswer = answerRepository.findById( answerReport.getAnswerId());
                answerRepository.deleteById(reportedAnswer.get().getId());

                // 更新举报记录状态为已批准
                answerReport.setStatus(AnswerReport.ReportStatus.APPROVED);
                answerReportRepository.save(answerReport);

//                 通知回答作者
                Notification notification = new Notification();
                notification.setUserId(reportedAnswer.get().getAuthor().getId());
                notification.setNotificationContent("您的回答已被删除，原因：违反社区规定");
                notification.setNotificationTime(LocalDateTime.now());
                adminNotificationRepository.save(notification);
            } else {
                // 拒绝审批，更新举报状态为已拒绝
                answerReport.setStatus(AnswerReport.ReportStatus.REJECTED);
                answerReportRepository.save(answerReport);

                // 通知举报者
//                notificationService.sendNotification(
//                        answerReport.getUserId(),
//                        "您提交的举报已被审核，该回答未违反社区规定"
//                );
            }
        } else {
            throw new RuntimeException("举报状态不是待处理状态，无法审批");
        }
    }

    // 管理员分页获取所有举报记录
    public  Page<AdminAnswerReportDTO> getAllReports(ReportStatus status, Pageable pageable) {
        Page<AnswerReport> answerReports;
        if (status != null) {
            answerReports = answerReportRepository.findByStatus(status, pageable);
        } else {
            answerReports = answerReportRepository.findAll(pageable);
        }

        // 将 AnswerReport 转换为 AdminAnswerReportDTO
        return new PageImpl<>(
                answerReports.getContent().stream()
                        .map(answerReport -> {
                            String answerContent = answerService.getAnswerContentById(answerReport.getAnswerId());
                            Optional<Question> question = questionRepository.findById(answerReport.getQuestionId());
                            return AdminAnswerReportDTO.fromAnswerReport(answerReport, question.get(),answerContent);
                        })
                        .collect(Collectors.toList()),
                pageable,
                answerReports.getTotalElements()
        );
    }


}