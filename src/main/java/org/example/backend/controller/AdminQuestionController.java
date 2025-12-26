package org.example.backend.controller;

import org.example.backend.dto.AdminQuestionListDTO;
import org.example.backend.dto.PageResponse;
import org.example.backend.model.Question;
import org.example.backend.model.Question.QuestionStatus;
import org.example.backend.model.QuestionReport;
import org.example.backend.model.ResponseStatus;
import org.example.backend.service.AdminQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.backend.utils.ApiResponse;
import org.example.backend.dto.AdminQuestionReportDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    @Autowired
    private AdminQuestionService adminQuestionService;
    @GetMapping
    public ResponseEntity<?> getQuestionList(
            @RequestParam(name = "status", required = false) QuestionStatus status,
            @RequestParam(name = "isReported", required = false) Boolean isReported,
            @RequestParam(name = "authorId", required = false) Long authorId,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size",defaultValue = "10") int size){
        try {
            Pageable pageable = PageRequest.of(page-1, size);
            Page<Question> questions = adminQuestionService.getQuestionListWithPagination(status, isReported, authorId, title, content, pageable);
            List<AdminQuestionListDTO> questionDTOs = questions.getContent().stream()
                    .map(AdminQuestionListDTO::fromQuestion)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("data", questionDTOs);
            response.put("total", questions.getTotalElements());
            response.put("pages", questions.getTotalPages());
            response.put("page", questions.getNumber());
            response.put("size", 10);
            return ResponseEntity.ok(response);
        }catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 封禁问题
    @PutMapping("/{questionId}/ban")
    public ApiResponse banQuestion(@PathVariable("questionId") Long questionId) {
        return adminQuestionService.banQuestion(questionId);
    }

    // 删除问题
    @DeleteMapping("/{questionId}")
    public ApiResponse deleteQuestion(@PathVariable("questionId") Long questionId) {
        return adminQuestionService.deleteQuestion(questionId);
    }

    // 审核问题
    @PutMapping("/{questionId}/review")
    public ApiResponse reviewQuestion(@PathVariable("questionId") Long questionId, @RequestParam("approved") boolean approved) {
        return adminQuestionService.reviewQuestion(questionId, approved);
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse> getQuestionReportList(
            @RequestParam(name="status", required = false) QuestionReport.ReportStatus status,
            @RequestParam(name="startTime", required = false) LocalDateTime startTime,
            @RequestParam(name="endTime", required = false) LocalDateTime endTime,
            @RequestParam(name="questionId", required = false) Long questionId,
            @RequestParam(name="reporterId", required = false) Long reporterId,
            @RequestParam(name="page", defaultValue = "1") int page,
            @RequestParam(name="size", defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page - 1, size);
            Page<AdminQuestionReportDTO> dtoPage = adminQuestionService.getQuestionReportListDTO(
                    status, startTime, endTime, questionId, reporterId, pageable);

            // 使用PageResponse封装分页数据
            PageResponse<AdminQuestionReportDTO> pageResponse = PageResponse.fromPage(dtoPage);

            // 将PageResponse设置到ApiResponse中
            ApiResponse apiResponse = new ApiResponse(
                    ResponseStatus.SUCCESS.getCode(),
                    true,
                    "获取成功",
                     pageResponse
            );

            return ResponseEntity.ok(apiResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(ResponseStatus.INTERNAL_SERVER_ERROR.getCode(), false, e.getMessage(), null)
            );
        }
    }
    // 审核举报
    @PutMapping("/reports/{reportId}/review")
    public ApiResponse reviewReport(@PathVariable("reportId") Long reportId, @RequestParam("approved") boolean approved) {

        return adminQuestionService.reviewReport(reportId, approved);
    }
}