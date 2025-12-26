package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.dto.QuestionCreateRequestDTO;
import org.example.backend.dto.QuestionDetailDTO;
import org.example.backend.dto.QuestionResponseDTO;
import org.example.backend.model.Question;
import org.example.backend.model.QuestionImage;
import org.example.backend.model.User;
import org.example.backend.model.ResponseStatus;
import org.example.backend.repository.AnswerCommentRepository;
import org.example.backend.repository.AnswerImageRepository;
import org.example.backend.repository.QuestionImageRepository;
import org.example.backend.repository.QuestionVoteRepository;
import org.example.backend.service.*;
import org.example.backend.utils.ApiResponse;
import org.example.backend.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionService questionService;
    private final QuestionVoteRepository questionVoteRepository;
    private final UserService userService;
    private final QuestionImageService questionImageService;
    private final ImageUploadService uploadService;
    private final QuestionImageRepository questionImageRepository;
    private final AnswerImageRepository answerImageRepository;
    private final AnswerCommentRepository answerCommentRepository;

    public QuestionController(
            QuestionService questionService,
            QuestionVoteRepository questionVoteRepository,
            UserService userService,
            QuestionImageService questionImageService,
            ImageUploadService uploadService,
            QuestionImageRepository questionImageRepository,
            AnswerImageRepository answerImageRepository,
            AnswerCommentRepository answerCommentRepository) {
        this.questionService = questionService;
        this.questionVoteRepository = questionVoteRepository;
        this.userService = userService;
        this.questionImageService = questionImageService;
        this.uploadService = uploadService;
        this.questionImageRepository = questionImageRepository;
        this.answerImageRepository = answerImageRepository;
        this.answerCommentRepository = answerCommentRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getQuestions(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        try {
            System.out.println("问题列表请求");
            Pageable pageable = PageRequest.of(page - 1, size);
            PageResponse<QuestionResponseDTO> response = questionService.getAllQuestions(pageable);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    ResponseStatus.SUCCESS.getMessage(),
                    response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse> searchQuestions(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "关键字不能为空或仅包含空格"
                ));
            }
            Pageable pageable = PageRequest.of(page - 1, 10);
            Page<Question> questions = questionService.searchQuestions(keyword, pageable);
            PageResponse<QuestionResponseDTO> response = PageResponse.fromPage(
                    questions.map(question -> QuestionResponseDTO.fromQuestion(question, questionImageService))
            );
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    ResponseStatus.SUCCESS.getMessage(),
                    response
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createQuestion(
            @RequestBody QuestionCreateRequestDTO request,
            HttpServletRequest httpServletRequest) {
        try {
            String authorizationHeader = httpServletRequest.getHeader("Authorization");
            String token = null;
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                // 提取 token
                token = authorizationHeader.substring(7);
            }else{
                return ResponseEntity.ok(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "未获取到token，请先登录"
                ));
            }
            QuestionDetailDTO dto = questionService.createQuestion(
                    request.title(),
                    request.content(),
                    token,
                    request.categoryId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    ResponseStatus.CREATED.getCode(),
                    ResponseStatus.CREATED.getMessage(),
                    dto
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse> getMyQuestions(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page", defaultValue = "10") int size,

            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "keyword", defaultValue = "") String keyword
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) userService.loadUserByUsername(auth.getName());
            if (user.getId().equals(userId)) {
                Page<Question> questions = questionService.getMyQuestionsByParams(page-1, size, status, userId, keyword);
                PageResponse<QuestionResponseDTO> response = PageResponse.fromPage(
                        questions.map(question -> QuestionResponseDTO.fromQuestion(question, questionImageService))
                );
                return ResponseEntity.ok(ApiResponse.success(
                        ResponseStatus.SUCCESS.getCode(),
                        ResponseStatus.SUCCESS.getMessage(),
                        response
                ));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.FORBIDDEN.getCode(),
                        "你无权查询"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateQuestion(@PathVariable("id") Long id,
                                                      @RequestBody Map<String, Object> request,
                                                      HttpServletRequest httpServletRequest) {
        try {
            String authorizationHeader = httpServletRequest.getHeader("Authorization");
            String token = null;
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                // 提取 token
                token = authorizationHeader.substring(7);
            }else{
                return ResponseEntity.ok(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "未获取到token，请先登录"
                ));
            }
            System.out.println("token111:"+token);
            String title = (String) request.get("title");
            String content = (String) request.get("content");
            String categoryId = (String) request.get("categoryId");
            QuestionDetailDTO dto= questionService.updateQuestion(id, title, content, categoryId,token);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    ResponseStatus.SUCCESS.getMessage(),
                    dto
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResponse> getQuestionDetail(@PathVariable("id") Long id) {
        QuestionDetailDTO questionDetailDTO = questionService.getQuestionDetailById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ResponseStatus.SUCCESS.getCode(),
                ResponseStatus.SUCCESS.getMessage(),
                questionDetailDTO
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteQuestion(@PathVariable("id") Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            questionService.deleteQuestion(id, username);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "问题删除成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }


    // 标记回答为解决答案
    @PostMapping("/{questionId}/mark-solved/{answerId}")
    public ResponseEntity<ApiResponse> markAnswerAsSolved(
            @PathVariable("questionId") Long questionId,
            @PathVariable("answerId") Long answerId
    ) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            Long userId = user.getId();
            QuestionDetailDTO dto = questionService.markAnswerAsSolved(questionId, answerId, userId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "回答已标记为解决答案",
                    dto
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    // 取消标记解决答案
    @PostMapping("/{questionId}/unmark-solved")
    public ResponseEntity<ApiResponse> unmarkAnswerAsSolved(
            @PathVariable("questionId") Long questionId
    ) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            Long userId = user.getId();
            QuestionDetailDTO dto = questionService.unmarkAnswerAsSolved(questionId, userId);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "解决答案标记已取消",
                    dto
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse> reportQuestion(@PathVariable("id") Long id, @RequestBody Map<String, String> request) {
        try {
            String reason = request.get("reason");
            String description = request.get("description");
            if(reason.length()>30){
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "原因字数过长"
                ));
            }
            if (reason == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "举报原因不能为空"
                ));
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            questionService.reportQuestion(id, reason, description, authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报提交成功，我们将尽快处理"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}/report")
    public ResponseEntity<ApiResponse> updateQuestionReport(
            @PathVariable("id") Long reportId,
            @RequestBody Map<String, String> request
    ) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            String reason = request.get("reason");
            String description = request.get("description");
            if (reason == null || description == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "举报原因和详情不能为空"
                ));
            }
            questionService.updateQuestionReport(reportId, reason, description, username);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "举报提交成功，我们将尽快处理"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse> likeQuestion(@PathVariable("id") Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            String message = questionService.voteQuestion(id, true, username);
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    message
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/dislike")
    public ResponseEntity<ApiResponse> dislikeQuestion(@PathVariable("id") Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String message = questionService.voteQuestion(id, false, authentication.getName());
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    message
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }

    @PostMapping("/images/upload")
    public ResponseEntity<ApiResponse> uploadQuestionImage(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String imagePath = uploadService.uploadTempImage(file,"temp_question");
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

    @PostMapping("/images/Cover")
    public ResponseEntity<ApiResponse> uploadQuestionCover (
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String imagePath = uploadService.uploadTempImage(file,"temp_cover");
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

    @DeleteMapping("/{questionId}/images/{imageId}")
    public ResponseEntity<ApiResponse> deleteQuestionImage(
            @PathVariable("questionId") Long questionId,
            @PathVariable("imageId") Long imageId
    ) {
        try {
            Map<String, Object> result = questionImageService.deleteImageById(imageId, questionId);
            if ((boolean) result.get("success")) {
                return ResponseEntity.ok(ApiResponse.success(
                        ResponseStatus.SUCCESS.getCode(),
                        "删除图片成功"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "删除图片失败" + result.get("message")
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        }
    }
}