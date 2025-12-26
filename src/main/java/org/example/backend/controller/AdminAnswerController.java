package org.example.backend.controller;

import org.example.backend.dto.AdminAnswerReportDTO;
import org.example.backend.dto.PageResponse;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.ResponseStatus;
import org.example.backend.service.AdminAnswerReportService;
import org.example.backend.service.AnswerReportService;
import org.example.backend.utils.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.backend.model.AnswerReport.ReportStatus;

@RestController
@RequestMapping("/api/admin/answers")
public class AdminAnswerController {
    private final AdminAnswerReportService adminAswerReportService;

    public AdminAnswerController(AdminAnswerReportService adminAswerReportService) {
        this.adminAswerReportService = adminAswerReportService;
    }

    // 管理员审批举报
    @PutMapping("/{reportId}/approve")
    public ResponseEntity<ApiResponse> approveReport(@PathVariable("reportId") Long reportId, @RequestParam("isApproved") boolean isApproved) {
        try {
            adminAswerReportService.approveReport(reportId, isApproved);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "审批处理成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse> getAllReports(
            @RequestParam(name = "status", required = false) ReportStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page - 1, size);
            // 调用修改后的服务层方法，获取 Page<AdminAnswerReportDTO>
            Page<AdminAnswerReportDTO> answerReports = adminAswerReportService.getAllReports(status, pageable);
            // 使用 AdminAnswerReportDTO 构建 PageResponse
            PageResponse<AdminAnswerReportDTO> response = PageResponse.fromPage(answerReports);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "获取举报记录成功",
                    response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }
}