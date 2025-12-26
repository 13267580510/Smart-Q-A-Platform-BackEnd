// QuestionService.java
package org.example.backend.service;

import org.example.backend.dto.PageResponse;
import org.example.backend.dto.QuestionDetailDTO;
import org.example.backend.dto.QuestionResponseDTO;
import org.example.backend.model.*;
import org.example.backend.repository.*;
import org.example.backend.utils.JwtUtils;
import org.example.backend.utils.UserRoleUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionService {

    private final AnswerImageRepository answerImageRepository;
    private final QuestionImageRepository questionImageRepository;
    private final UserRoleUtils userRoleUtils;


    private final QuestionRepository questionRepository;
    private final UserService userService;
    private final AnswerRepository answerRepository;
    private final QuestionVoteRepository questionVoteRepository;
    private final QuestionReportRepository questionReportRepository;
    private final AnswerCommentRepository answerCommentRepository;
    private final QuestionImageService questionImageService;
    private final UserRepository userRepository;
    private final ImageUploadService imageUploadService;
    public QuestionService(
            QuestionRepository questionRepository,
            UserService userService,
            UserRepository userRepository,
            AnswerRepository answerRepository,
            QuestionReportRepository questionReportRepository,
            QuestionVoteRepository questionVoteRepository,
            AnswerImageRepository answerImageRepository,
            QuestionImageRepository questionImageRepository,
            AnswerCommentRepository answerCommentRepository,
            UserRoleUtils userRoleUtils,
            QuestionImageService questionImageService,
            ImageUploadService imageUploadService) {
        this.questionRepository = questionRepository;
        this.userService = userService;
        this.answerRepository = answerRepository;
        this.questionReportRepository = questionReportRepository;
        this.questionVoteRepository = questionVoteRepository;
        this.answerImageRepository = answerImageRepository;
        this.questionImageRepository = questionImageRepository;
        this.answerCommentRepository = answerCommentRepository;
        this.userRoleUtils = userRoleUtils;
        this.questionImageService = questionImageService;
        this.userRepository= userRepository;
        this.imageUploadService = imageUploadService;
    }
    @Cacheable(value = "questionList", key = "#pageable?.pageNumber?.toString() + '_' + #pageable?.pageSize?.toString()")
    public PageResponse<QuestionResponseDTO> getAllQuestions(Pageable pageable) {
        if (pageable == null) {
            pageable = PageRequest.of(0, 10);
        }
        Page<Question> questions = questionRepository.findAll(pageable);
        return PageResponse.fromPage(
                questions.map(question -> QuestionResponseDTO.fromQuestion(question, questionImageService))
        );
    }

    @CacheEvict(value = "questionsDetail", key = "#a0")
    public QuestionDetailDTO createQuestion(String title, String content,String token,String categoryId) {
        try {
            System.out.println("执行！");
            Long userId = JwtUtils.getUserIdFromToken(token);
            User user = userService.findByIdReUser(userId);
            Question question = new Question();
            question.setTitle(title);
            System.out.println("nickname:" + user.getNickname());
            question.setAuthor(user);

            // 创建QuestionContent并关联
            QuestionContent questionContent = new QuestionContent();
            // 正则表达式：匹配 src 属性值中的 "temp_" 字符串（不区分大小写）
            Pattern pattern1 = Pattern.compile("src=['\"](.*?temp_)(.*?)['\"]", Pattern.CASE_INSENSITIVE);
            Matcher matcher1 = pattern1.matcher(content);

            // 构建替换后的内容
            StringBuffer result = new StringBuffer();
            while (matcher1.find()) {
                // 提取匹配到的分组：group(1) 是 "temp_" 前的路径，group(2) 是剩余部分
                String tempPart = matcher1.group(1); // 例如："http://127.0.0.1:8080/uploads/"
                String restPart = matcher1.group(2); // 例如："4f38eb1e-d103-4382-9903-55dacb3967db_100 (3).png"

                // 删除 "temp_" 部分
                String newPath = tempPart.replace("temp_", "") + restPart;

                // 替换到原内容中
                matcher1.appendReplacement(result, "src='" + newPath + "'");
            }
            matcher1.appendTail(result);
            questionContent.setContent(result.toString());
            questionContent.setQuestion(question);
            question.setContent(questionContent);
            question.setCategoryId(Long.parseLong(categoryId));

            // 保存问题到数据库
            questionRepository.save(question);

            // 获取创建后的问题的questionId
            Long questionId = question.getId();

            // 提取content中所有img标签的src属性
            Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String imgUrl = matcher.group(1);
                // 获取图片名称
                String imageName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
                imgUrl = imageUploadService.saveImage("uploads/temp_question",imageName,"uploads/question");
                // 创建QuestionImage对象并保存到数据库
                questionImageService.saveImage(questionId,imgUrl);
            }

            return QuestionDetailDTO.fromQuestion(
                    question,
                    questionVoteRepository,
                    userService,
                    questionImageRepository,
                    answerImageRepository,
                    answerCommentRepository);

        } catch (Exception e) {
            // 记录异常日志
            System.out.println("创建问题失败: {}" + e.getMessage() + e);
            // 重新抛出异常，让全局异常处理器处理
            throw new RuntimeException("创建问题失败", e);
        }
    }


    public QuestionDetailDTO getQuestionDetailById(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));
        // 将问题的浏览量加 1
        question.setViewCount(question.getViewCount() + 1);
        questionRepository.save(question);
        return QuestionDetailDTO.fromQuestion(question,questionVoteRepository,userService,questionImageRepository,answerImageRepository,answerCommentRepository);
    }

    @Transactional
    @CacheEvict(value = "questionsDetail", key = "#a0")
    public QuestionDetailDTO updateQuestion(Long id, String title, String content, String categoryId, String token) throws IOException {
        System.out.println("执行修改问题i");
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        Long userId = JwtUtils.getUserIdFromToken(token);
        System.out.println("userId:" + userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        System.out.println("question.getAuthor():" + question.getAuthor());
        System.out.println("user:" + user);

        if (!question.getAuthor().equals(user)) {
            throw new RuntimeException("无权限修改该问题");
        }

        // 提取原始content中的img标签src
        Set<String> originalImgUrls = extractImgUrls(question.getContent().getContent());

        // 处理更新后的content
        if (content != null) {
            QuestionContent questionContent = question.getContent();
            if (questionContent == null) {
                questionContent = new QuestionContent();
                questionContent.setQuestion(question);
            }

            // 提取新content中的img标签src
            Set<String> newImgUrls = extractImgUrls(content);

            // 找出新增的图片URL
            Set<String> addedImgUrls = new HashSet<>(newImgUrls);
            addedImgUrls.removeAll(originalImgUrls);

            // 处理新增的图片：从临时目录移动到正式目录
            for (String imgUrl : addedImgUrls) {
                String imageName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
                String newUrl = imageUploadService.saveImage("uploads/temp_question", imageName, "uploads/question");

                // 替换content中的临时URL为正式URL
                content = content.replace(imgUrl, newUrl.replace("\\", "/"));

                // 保存图片信息到数据库
                questionImageService.saveImage(question.getId(), newUrl);
            }

            questionContent.setContent(content);
            question.setContent(questionContent);
        }

        if (title != null) {
            question.setTitle(title);
        }

        // 处理 categoryId
        if (categoryId != null) {
            question.setCategoryId(Long.valueOf(categoryId));
        }
        Question questionRes = questionRepository.save(question);
        return  QuestionDetailDTO.fromQuestion(
                questionRes,
                questionVoteRepository,
                userService,
                questionImageRepository,
                answerImageRepository,
                answerCommentRepository);
    }

    // 辅助方法：提取content中的img标签src
    private Set<String> extractImgUrls(String content) {
        Set<String> imgUrls = new HashSet<>();
        if (content == null) return imgUrls;

        Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            imgUrls.add(matcher.group(1));
        }

        return imgUrls;
    }


    // 新增删除问题方法，并增加权限鉴别

    @Transactional
    @CacheEvict(value = "questionsDetail", key = "#a0")
    public void deleteQuestion(Long questionId, String username) {
        // 先查找问题是否存在
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        // 根据用户名查找用户信息
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证当前用户是否为问题的作者
        if (!question.getAuthor().getId().equals(user.getId())) {
            throw new RuntimeException("无权限删除该问题");
        }

        // 获取该问题下的所有回答
        List<Answer> answers = question.getAnswers();
        if (!answers.isEmpty()) {
            // 删除所有回答
            answerRepository.deleteAll(answers);
        }

        // 删除问题
        questionRepository.delete(question);
    }


    //标记解决问题答案
    @Transactional
    @CacheEvict(value = "questionsDetail", key = "#questionId")
    public QuestionDetailDTO markAnswerAsSolved(Long questionId, Long answerId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        // 鉴权：检查当前用户的 ID 是否与问题的创建者 ID 一致
        if (!question.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("无权限标记该问题的解决答案");
        }

        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new RuntimeException("回答不存在"));

        if (!question.getAnswers().contains(answer)) {
            throw new RuntimeException("该回答不属于此问题");
        }

        question.setIsSolved(answer);
        questionRepository.save(question);

        return QuestionDetailDTO.fromQuestion(
                question,
                questionVoteRepository,
                userService,
                questionImageRepository,
                answerImageRepository,
                answerCommentRepository);
    }

    // 取消标记解决答案
    @Transactional
    @CacheEvict(value = "questionsDetail", key = "#questionId")
    public QuestionDetailDTO unmarkAnswerAsSolved(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        // 鉴权：检查当前用户的 ID 是否与问题的创建者 ID 一致
        if (!question.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("无权限取消标记该问题的解决答案");
        }

        question.setIsSolved(null);
        questionRepository.save(question);

        return QuestionDetailDTO.fromQuestion(
                question,
                questionVoteRepository,
                userService,
                questionImageRepository,
                answerImageRepository,
                answerCommentRepository);
    }


    @Transactional
    public void reportQuestion(Long questionId, String reason, String description, Authentication principal) {

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        User reporter = userService.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<QuestionReport.ReportStatus> statuses = Arrays.asList(
                QuestionReport.ReportStatus.PENDING,
                QuestionReport.ReportStatus.APPROVED
        );
        // 调用新方法进行检查
        boolean hasReported = questionReportRepository.existsByQuestionIdAndReporterIdAndStatusIn(questionId, reporter.getId(), statuses);
        if (hasReported) {
            throw new RuntimeException("你已举报过该问题，请勿重复提交");
        }

        QuestionReport report = new QuestionReport();
        report.setQuestion(question);
        report.setReporter(reporter);
        report.setReason(QuestionReport.ReasonStatus.valueOf(reason));
        report.setDescription(description);

        questionReportRepository.save(report);

        // 增加问题的举报次数
        question.setReportCount(question.getReportCount() + 1);
        questionRepository.save(question);
    }


    @Transactional
    public void updateQuestionReport(Long reportId, String reason, String description, String username) {

        // 根据举报ID查找问题举报记录
        QuestionReport report = questionReportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("问题举报记录不存在"));

        // 根据用户名查找用户信息
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证当前用户是否为问题举报的提交者
        if (!report.getReporter().getId().equals(user.getId())) {
            throw new RuntimeException("你无权修改");
        }

        // 检查问题举报状态是否为APPROVED或REJECTED
        if (report.getStatus() != QuestionReport.ReportStatus.APPROVED || report.getStatus() != QuestionReport.ReportStatus.REJECTED) {
            // 更新举报原因和描述
            report.setReason(QuestionReport.ReasonStatus.valueOf(reason));
            report.setDescription(description);
            report.setReportTime(LocalDateTime.now());

            // 保存更新后的问题举报记录
            questionReportRepository.save(report);
        } else {
            throw new RuntimeException("该问题举报状态不允许修改");
        }
    }








    @Transactional
    public String voteQuestion(Long questionId, Boolean voteType, String username) {
        // 查找问题，如果问题不存在则抛出异常
        Question question = findQuestionById(questionId);
        // 查找用户，如果用户不存在则抛出异常
        org.example.backend.model.User user = findUserByUsername(username);

        logVoteAction(username);

        // 检查用户是否已经对该问题进行了投票
        Optional<QuestionVote> existingVote = questionVoteRepository.findByUserIdAndQuestionId(user.getId(), questionId);

        if (existingVote.isPresent()) {
            return handleExistingVote(existingVote.get(), voteType);
        } else {
            return createNewVote(question, user, voteType);
        }
    }

    /**
     * 根据问题 ID 查找问题
     * @param questionId 问题 ID
     * @return 查找到的问题
     * @throws RuntimeException 如果问题不存在
     */
    private Question findQuestionById(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));
    }

    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 查找到的用户
     * @throws RuntimeException 如果用户不存在
     */
    private org.example.backend.model.User findUserByUsername(String username) {
        return userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    /**
     * 记录用户的投票操作
     * @param username 用户名
     */
    private void logVoteAction(String username) {
        System.out.println("当前用户 : " + username + " 执行点赞或点踩功能");
    }

    /**
     * 处理用户已经存在的投票情况
     * @param existingVote 已存在的投票记录
     * @param voteType 新的投票类型
     * @return 操作结果信息
     */
    private String handleExistingVote(QuestionVote existingVote, Boolean voteType) {
        System.out.println("是执行取消操作");
        if (existingVote.getVoteType().equals(voteType)) {
            // 用户已进行过相同的投票操作，取消投票
            questionVoteRepository.delete(existingVote);
            return getCancelVoteMessage(voteType);
        } else {
            // 用户更改投票类型，更新投票记录
            existingVote.setVoteType(voteType);
            questionVoteRepository.save(existingVote);
            return getVoteSuccessMessage(voteType);
        }
    }

    /**
     * 获取取消投票的消息
     * @param voteType 投票类型
     * @return 取消投票的消息
     */
    private String getCancelVoteMessage(Boolean voteType) {
        if (voteType) {
            System.out.println("yes");
            return "取消点赞成功";
        } else {
            return "取消点踩成功";
        }
    }

    /**
     * 获取投票成功的消息
     * @param voteType 投票类型
     * @return 投票成功的消息
     */
    private String getVoteSuccessMessage(Boolean voteType) {
        if (voteType) {
            return "点赞成功";
        } else {
            return "点踩成功";
        }
    }

    /**
     * 创建新的投票记录
     * @param question 问题对象
     * @param user 用户对象
     * @param voteType 投票类型
     * @return 投票操作结果信息
     */
    private String createNewVote(Question question, org.example.backend.model.User user, Boolean voteType) {
        QuestionVote newVote = new QuestionVote();
        newVote.setUser(user);
        newVote.setQuestion(question);
        newVote.setVoteType(voteType);
        questionVoteRepository.save(newVote);
        return voteType ? "点赞操作成功" : "点踩操作成功";
    }

    //模糊搜寻问题
    public Page<Question> searchQuestions(String keyword, Pageable pageable) {
        return questionRepository.searchQuestions(keyword, pageable);
    }
    public Page<Question> getMyQuestionsByParams(int page, int size, String status, Long userId, String keyword) {
        Pageable pageable = PageRequest.of(page - 1, size);
        if ("ALL".equals(status)) {
            if (keyword.isEmpty()) {
                return questionRepository.findByAuthorId(userId, pageable);
            } else {
                return questionRepository.findByAuthorIdAndKeyword(userId, keyword, pageable);
            }
        } else {
            if (keyword.isEmpty()) {
                return questionRepository.findByAuthorIdAndStatus(userId, status, pageable);
            } else {
                return questionRepository.findByAuthorIdAndStatusAndKeyword(userId, status, keyword, pageable);
            }
        }
    }

}