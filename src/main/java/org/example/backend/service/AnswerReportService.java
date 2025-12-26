// AnswerReportService.java
package org.example.backend.service;

import org.example.backend.model.Answer;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.User;
import org.example.backend.repository.AnswerReportRepository;
import org.example.backend.repository.AnswerRepository;
import org.example.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnswerReportService {
    private final AnswerReportRepository answerReportRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    public AnswerReportService(AnswerReportRepository answerReportRepository,
                               AnswerRepository answerRepository,
                               UserRepository userRepository) {
        this.answerReportRepository = answerReportRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AnswerReport reportAnswer(Long userId, Long answerId, String reason) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Answer reportedAnswer = answerRepository.findById(answerId)
                .orElseThrow(() -> new RuntimeException("回答不存在"));

        // 检查用户是否已经举报过该回答（状态为 PENDING）
        boolean alreadyReported = answerReportRepository
                .findByUserIdAndAnswerIdAndStatus(userId, answerId, AnswerReport.ReportStatus.PENDING)
                .isPresent();

        if (alreadyReported) {
            throw new RuntimeException("你已经举报过该回答，正在处理中");
        }

        AnswerReport answerReport = new AnswerReport();
        answerReport.setUserId(userId);
        answerReport.setAnswerId(answerId);
        answerReport.setReason(reason);
        answerReport.setCreatedTime(LocalDateTime.now());
        answerReport.setStatus(AnswerReport.ReportStatus.PENDING);

        // 设置回答作者 ID 和问题 ID
        answerReport.setAnswerAuthorId(reportedAnswer.getAuthor().getId());
        answerReport.setQuestionId(reportedAnswer.getQuestion().getId());

        return answerReportRepository.save(answerReport);
    }


}