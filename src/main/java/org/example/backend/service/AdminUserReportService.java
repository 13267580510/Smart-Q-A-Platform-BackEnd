// AdminUserReportService.java
package org.example.backend.service;

import org.example.backend.model.User;
import org.example.backend.model.UserReport;
import org.example.backend.repository.UserReportRepository;
import org.example.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AdminUserReportService {
    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;

    public AdminUserReportService(UserReportRepository userReportRepository, UserRepository userRepository) {
        this.userReportRepository = userReportRepository;
        this.userRepository = userRepository;
    }

    public UserReport createReport(Long reporterId, Long reportedUserId, String reportReason) {
        // 检查是否存在未处理的举报
        Optional<UserReport> pendingReport = userReportRepository.findPendingReport(reporterId, reportedUserId);
        if (pendingReport.isPresent()) {
            throw new IllegalArgumentException("您已提交对该用户的举报，正在处理中");
        }

        // 创建新举报
        UserReport report = new UserReport();
        report.setReporterId(reporterId);
        report.setReportedUserId(reportedUserId);
        report.setReportTime(LocalDateTime.now());
        report.setReportReason(reportReason);
        report.setProcessed(false);
        return userReportRepository.save(report);
    }
//    public UserReport createReport(Long reporterId, Long reportedUserId, String reportReason) {
//        UserReport report = new UserReport();
//        report.setReporterId(reporterId);
//        report.setReportedUserId(reportedUserId);
//        report.setReportTime(LocalDateTime.now());
//        report.setReportReason(reportReason);
//        report.setProcessed(false); // 初始化为未处理
//        return userReportRepository.save(report);
//    }

    public void banUser(Long userId, LocalDateTime banEndTime) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus("BANNED");
        user.setBanEndTime(banEndTime);
        userRepository.save(user);
    }

    public void unbanUser(Long userId, Long reportId) {
        // 检查用户是否存在且处于封禁状态
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        System.out.println("user:"+user.toString());
        if (!user.getStatus().equals("BANNED")) {
            throw new IllegalArgumentException("用户不处于封禁状态，无法解封");
        }

        // 检查举报记录是否存在且结果为封禁用户
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("举报记录不存在"));

        if (!report.isProcessed() || report.getResult() != UserReport.ProcessingResult.BAN_USER) {
            throw new IllegalArgumentException("该举报记录未处理或结果不是封禁用户");
        }

        // 解封用户
        user.setStatus("ACTIVE");
        user.setBanEndTime(null);
        userRepository.save(user);

        // 修改举报记录为驳回状态（修正：使用userReportRepository）
        report.setResult(UserReport.ProcessingResult.REJECT_REPORT);
        userReportRepository.save(report); // 修正：调用正确的Repository
    }

    public void processReport(Long reportId, LocalDateTime banEndTime) {
        UserReport report = userReportRepository.findById(reportId).orElseThrow(() -> new IllegalArgumentException("举报记录不存在"));
        report.setProcessed(true); // 标记为已处理
        report.setResult(UserReport.ProcessingResult.BAN_USER);
        userReportRepository.save(report);
        // 假设处理举报就进行封禁操作
        banUser(report.getReportedUserId(), banEndTime);
    }
    public Page<UserReport> getFilteredReports(
            String reporterUsername,
            String reportedUsername,
            String reporterNickname,
            String reportedNickname,
            LocalDateTime reportTime,
            Boolean isProcessed,
            UserReport.ProcessingResult result,
            Pageable pageable
    ) {
        return userReportRepository.findByFilters(
                reporterUsername,
                reportedUsername,
                reporterNickname,
                reportedNickname,
                reportTime,
                isProcessed,
                result,
                pageable
        );
    }
    // 其他已有方法保持不变

    public void rejectReport(Long reportId) {
        UserReport report = userReportRepository.findById(reportId).orElseThrow(() -> new IllegalArgumentException("举报记录不存在"));
        if (report.isProcessed()) {
            throw new IllegalArgumentException("该举报已处理，不能再次驳回");
        }
        report.setProcessed(true); // 标记为已处理
        report.setResult(UserReport.ProcessingResult.REJECT_REPORT); // 设置处理结果为驳回举报
        userReportRepository.save(report);
    }

}