package org.example.backend.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.backend.dto.AdminQuestionReportDTO;
import org.example.backend.model.Question;
import org.example.backend.model.QuestionReport;
import org.example.backend.model.User;
import org.example.backend.model.Question.QuestionStatus;
import org.example.backend.model.QuestionReport.ReportStatus;
import org.example.backend.model.ResponseStatus;
import org.example.backend.repository.QuestionRepository;
import org.example.backend.repository.QuestionReportRepository;
import org.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.example.backend.utils.ApiResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.backend.service.AdminNotificationService;
@Service
public class AdminQuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReportRepository questionReportRepository;

    @Autowired
    private UserRepository userRepository;

    private AdminNotificationService adminNotificationService;
    public AdminQuestionService(QuestionRepository questionRepository,QuestionReportRepository questionReportRepository,UserRepository userRepository,AdminNotificationService adminNotificationService) {
            this.questionRepository = questionRepository;
            this.questionReportRepository = questionReportRepository;
            this.userRepository = userRepository;
            this.adminNotificationService = adminNotificationService;
    }
    // 获取问题列表（原方法保留）
    public List<Question> getQuestionList(Question.QuestionStatus status, Boolean isReported, Long authorId, String title, String content) {
        if (title != null) {
            title = "%" + title + "%";
        }
        if (content != null) {
            content = "%" + content + "%";
        }
        return questionRepository.findQuestions(status, isReported, authorId, title, content);
    }

    // 获取问题列表（支持分页）
    public Page<Question> getQuestionListWithPagination(Question.QuestionStatus status, Boolean isReported, Long authorId, String title, String content, Pageable pageable) {
        if (title != null) {
            title = "%" + title + "%";
        }
        if (content != null) {
            content = "%" + content + "%";
        }
        return questionRepository.findQuestionsWithPagination(status, isReported, authorId, title, content, pageable);
    }

    // 封禁问题
    public ApiResponse banQuestion(Long questionId) {
        try {
            Optional<Question> optionalQuestion = questionRepository.findById(questionId);
            if (optionalQuestion.isPresent()) {
                Question question = optionalQuestion.get();
                if (question.getStatus() == QuestionStatus.NORMAL) {
                    question.setStatus(QuestionStatus.CLOSED);
                    questionRepository.save(question);
                    notifyUser(question.getAuthor().getId(), "你的问题已被封禁，请重新提交审核。");
                    return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "问题封禁成功");
                } else {
                    return ApiResponse.error(ResponseStatus.BAD_REQUEST.getCode(), "问题状态不是正常状态，无法封禁");
                }
            } else {
                return ApiResponse.error(ResponseStatus.NOT_FOUND.getCode(), "未找到该问题，无法封禁");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "问题封禁失败：" + e.getMessage());
        }
    }

    public ApiResponse deleteQuestion(Long questionId) {
        try {
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new EntityNotFoundException("问题不存在，ID: " + questionId));

            // 断开与 QuestionContent 的关联
            if (question.getContent() != null) {
                question.getContent().setQuestion(null);
                question.setContent(null);
            }

            // 清空 answers 列表
            question.getAnswers().clear();

            // 清空 reports 列表
            question.getReports().clear();

            // 设置 isSolved 为 null
            question.setIsSolved(null);

            // 执行删除
            questionRepository.delete(question);

            notifyUser(question.getAuthor().getId(), "你的问题已被删除。");
            return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "问题删除成功");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(ResponseStatus.NOT_FOUND.getCode(), e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "问题删除失败：" + e.getMessage());
        }
    }
    // 审核问题
    public ApiResponse reviewQuestion(Long questionId, boolean approved) {
        try {
            Optional<Question> optionalQuestion = questionRepository.findById(questionId);
            if (optionalQuestion.isPresent()) {
                Question question = optionalQuestion.get();
                if (question.getStatus() == QuestionStatus.CLOSED||question.getStatus() == QuestionStatus.PENDING) {
                    if (approved) {
                        System.out.println("修改问题状态为NORMAL");
                        question.setStatus(QuestionStatus.NORMAL);
                    }
                    questionRepository.save(question);
                    String message = approved ? "你的问题已通过审核。" : "你的问题未通过审核，请修改后重新提交。";
                    notifyUser(question.getAuthor().getId(), message);
                    return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), message);
                } else {
                    return ApiResponse.error(ResponseStatus.BAD_REQUEST.getCode(), "问题状态不是CLOSE或PENDING状态，无需审核");
                }
            } else {
                return ApiResponse.error(ResponseStatus.NOT_FOUND.getCode(), "未找到该问题，无法审核");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "问题审核失败：" + e.getMessage());
        }
    }

    // 审核举报
    public ApiResponse reviewReport(Long reportId, boolean approved) {
        try {
            Optional<QuestionReport> optionalReport = questionReportRepository.findById(reportId);
            if (optionalReport.isPresent()) {
                QuestionReport report = optionalReport.get();
                if (report.getStatus() == ReportStatus.PENDING) {
                    if (approved) {
                        report.getQuestion().setStatus(QuestionStatus.CLOSED);
                        report.setStatus(ReportStatus.APPROVED);
                    } else {
                        report.getQuestion().setStatus(QuestionStatus.NORMAL);
                        report.setStatus(ReportStatus.REJECTED);
                    }
                    questionReportRepository.save(report);
                    questionRepository.save(report.getQuestion());
                    String message = approved ? "你举报的问题已被封禁。" : "你举报的问题未通过审核。";
                    notifyUser(report.getReporter().getId(), message);
                    return ApiResponse.success(ResponseStatus.SUCCESS.getCode(), "举报审核成功");
                } else {
                    return ApiResponse.error(ResponseStatus.BAD_REQUEST.getCode(), "举报状态不是待审核状态，无法审核");
                }
            } else {
                return ApiResponse.error(ResponseStatus.NOT_FOUND.getCode(), "未找到该举报，无法审核");
            }
        } catch (Exception e) {
            return ApiResponse.error(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "举报审核失败：" + e.getMessage());
        }
    }

    // 获取问题举报列表
    public Page<QuestionReport> getQuestionReportList(ReportStatus status, LocalDateTime startTime, LocalDateTime endTime, Long questionId, Long reporterId, Pageable pageable) {
        return questionReportRepository.findQuestionReports(status, startTime, endTime, questionId, reporterId, pageable);
    }

    // 服务层方法
    public Page<AdminQuestionReportDTO> getQuestionReportListDTO(
            QuestionReport.ReportStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long questionId,
            Long reporterId,
            Pageable pageable) {

        Page<QuestionReport> reportPage = getQuestionReportList(status, startTime, endTime, questionId, reporterId, pageable);

        return reportPage.map(AdminQuestionReportDTO::fromQuestionReport);
    }
    // 通知用户
    private void notifyUser(Long userId, String message) {
        // 这里可以实现具体的通知逻辑，如发送邮件、短信等
        adminNotificationService.publishNotificationToUser(userId, message);
    }
}