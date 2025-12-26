package org.example.backend.controller;

import org.example.backend.dto.PageResponse;
import org.example.backend.dto.UserReportDTO;
import org.example.backend.model.ResponseStatus;
import org.example.backend.model.User;
import org.example.backend.model.UserReport;
import org.example.backend.repository.UserRepository;
import org.example.backend.service.AdminUserReportService;
import org.example.backend.utils.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class AdminUserReportController {
    private final AdminUserReportService adminUserReportService;
    private final UserRepository userRepository;

    public AdminUserReportController(AdminUserReportService adminUserReportService, UserRepository userRepository) {
        this.adminUserReportService = adminUserReportService;
        this.userRepository = userRepository;
    }


    @PostMapping
    public ResponseEntity<ApiResponse> createReport(@RequestBody Map<String, Object> request) {
        try {
            Long reporterId = ((Number) request.get("reporterId")).longValue();
            Long reportedUserId = ((Number) request.get("reportedUserId")).longValue();
            String reportReason = (String) request.get("reportReason");

            UserReport report = adminUserReportService.createReport(reporterId, reportedUserId, reportReason);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报成功",
                    report
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    "创建举报失败：" + e.getMessage()
            ));
        }
    }
    @GetMapping("/getReportList")
    public ResponseEntity<ApiResponse> getFilteredReports(
            @RequestParam(value = "reporterUsername", required = false) String reporterUsername,
            @RequestParam(value = "reportedUsername", required = false) String reportedUsername,
            @RequestParam(value = "reporterNickname", required = false) String reporterNickname,
            @RequestParam(value = "reportedNickname", required = false) String reportedNickname,
            @RequestParam(value = "reportTime", required = false)  @DateTimeFormat(pattern = "yyyy/M/d HH:mm:ss") LocalDateTime reportTime,
            @RequestParam(value = "isProcessed", required = false) Boolean isProcessed,
            @RequestParam(value = "result", required = false) UserReport.ProcessingResult result,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page - 1, size);
            Page<UserReport> reportsPage = adminUserReportService.getFilteredReports(
                    reporterUsername,
                    reportedUsername,
                    reporterNickname,
                    reportedNickname,
                    reportTime,
                    isProcessed,
                    result,
                    pageable
            );

            PageResponse<UserReportDTO> reportDTOs = PageResponse.fromPage(
                    reportsPage.map(report -> {
                        User reporter = userRepository.findById(report.getReporterId()).orElse(null);
                        User reportedUser = userRepository.findById(report.getReportedUserId()).orElse(null);
                        return UserReportDTO.fromUserReport(report, reporter, reportedUser);
                    })
            );
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "获取筛选后的举报记录成功",
                    reportDTOs
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/{reportId}/ban")
    public ResponseEntity<ApiResponse> processReport(@PathVariable("reportId") Long reportId, @RequestBody Map<String, Object> request) {
        try {
            // 定义日期格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss");

            // 从请求中获取封禁结束时间字符串
            String banEndTimeStr = (String) request.get("banEndTime");

            // 解析日期字符串为 LocalDate 对象
            LocalDate banEndDate = banEndTimeStr != null ? LocalDate.parse(banEndTimeStr, formatter) : null;

            // 将 LocalDate 对象转换为 LocalDateTime 对象，时间部分设置为当天的开始时间
            LocalDateTime banEndTime = banEndDate != null ? banEndDate.atStartOfDay() : null;

            // 调用服务层方法处理举报
            adminUserReportService.processReport(reportId, banEndTime);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报处理成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/{reportId}/unban/{userId}")
    public ResponseEntity<ApiResponse> unbanUser(
            @PathVariable("reportId") Long reportId,
            @PathVariable("userId") Long userId
    ) {
        try {
            adminUserReportService.unbanUser(userId, reportId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "用户已解封，相关举报已驳回"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    "处理请求时发生未知错误"
            ));
        }
    }

    @PostMapping("/{reportId}/reject")
    public ResponseEntity<ApiResponse> rejectReport(@PathVariable("reportId") Long reportId) {
        try {
            adminUserReportService.rejectReport(reportId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报已驳回"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    "处理请求时发生未知错误"
            ));
        }
    }
}