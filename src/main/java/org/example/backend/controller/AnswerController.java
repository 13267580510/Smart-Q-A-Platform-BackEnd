package org.example.backend.controller;

import org.example.backend.model.Answer;
import org.example.backend.model.AnswerComment;
import org.example.backend.model.AnswerImage;
import org.example.backend.model.AnswerReport;
import org.example.backend.model.ResponseStatus;
import org.example.backend.service.*;
import org.example.backend.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/answers")
public class AnswerController {
    private final AnswerService answerService;
    private final AnswerReportService answerReportService;
    private final AnswerImageService answerImageService;
    private final ImageUploadService uploadService;
    private final AnswerCommentService answerCommentService;
    public AnswerController(
            AnswerService answerService,
            AnswerReportService answerReportService,
            AnswerImageService answerImageService,
            ImageUploadService uploadService,
            AnswerCommentService answerCommentService) {
        this.answerService = answerService;
        this.answerReportService = answerReportService;
        this.answerImageService = answerImageService;
        this.uploadService = uploadService;
        this.answerCommentService = answerCommentService;

    }

    @PostMapping
    public ResponseEntity<ApiResponse> createAnswer(@RequestBody Map<String, Object> request) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            LocalDateTime answerTime = LocalDateTime.parse((CharSequence) request.get("answerTime"), formatter);
            Long questionId = Long.parseLong(String.valueOf(request.get("questionId")));
            Long userId = ((Integer) request.get("userId")).longValue();
            String content = (String) request.get("content");

            if (questionId == null || userId == null || answerTime == null || content == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "请求数据不完整"
                ));
            }

           Answer res =  answerService.createAnswer(questionId, userId, answerTime, content);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "回答创建成功",
                    res
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 用户举报回答
    @PostMapping("/report")
    public ResponseEntity<ApiResponse> reportAnswer(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.parseLong((String) request.get("userId"));
            Long answerId = Long.parseLong((String) request.get("answerId"));
            String reason = (String) request.get("reason");

            if (userId == null || answerId == null || reason == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "请求数据不完整"
                ));
            }

            AnswerReport answerReport = answerReportService.reportAnswer(userId, answerId, reason);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报提交成功",
                    answerReport
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 上传图片到回答
    @PostMapping("/images/upload")
    public ResponseEntity<ApiResponse> uploadAnswerImage(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String imagePath =  uploadService.uploadTempImage(file,"temp_answer");
            return new ResponseEntity<>(ApiResponse.success(
                    ResponseStatus.CREATED.getCode(),
                    "图片上传成功",
                    imagePath
            ), HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 删除回答关联的图片
    @DeleteMapping("/{answerId}/images/{imageId}")
    public ResponseEntity<ApiResponse> deleteAnswerImage(
            @PathVariable("answerId") Long answerId,
            @PathVariable("imageId") Long imageId
    ) {
        try {
            answerImageService.deleteImagesByImageId(imageId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "图片删除成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 创建子回答（二级评论或多级评论）
    @PostMapping("/{answerId}/comments")
    public ResponseEntity<ApiResponse> createAnswerComment(
            @PathVariable("answerId") Long answerId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            Long userId = (Long)request.get("userId");
            String content = (String) request.get("content");
            Long parentCommentId = request.get("parentCommentId") != null ?
                    Long.parseLong((String) request.get("parentCommentId")) : null;

            if (userId == null || content == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "请求数据不完整"
                ));
            }

            AnswerComment comment = answerCommentService.saveComment(answerId, userId, content, parentCommentId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "子回答创建成功",
                    comment
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    "创建失败:"+e.getMessage()
            ));
        }
    }

    // 获取回答的所有子回答（二级评论）
    @GetMapping("/{answerId}/comments")
    public ResponseEntity<ApiResponse> getAnswerComments(@PathVariable("answerId") Long answerId) {
        try {
            List<AnswerComment> comments = answerCommentService.getCommentsByAnswerId(answerId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "获取子回答成功",
                    comments
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 获取某个评论的所有子评论
    @GetMapping("/comments/{parentCommentId}/children")
    public ResponseEntity<ApiResponse> getChildComments(@PathVariable("parentCommentId") Long parentCommentId) {
        try {
            List<AnswerComment> comments = answerCommentService.getCommentsByParentCommentId(parentCommentId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "获取子评论成功",
                    comments
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }
}