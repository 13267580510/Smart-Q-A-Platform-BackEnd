//answerservice.java
package org.example.backend.service;

import org.example.backend.dto.UserReplyDTO;
import org.example.backend.model.*;
import org.example.backend.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnswerService {
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final AnswerReportRepository answerReportRepository;
    private final AnswerCommentRepository answerCommentRepository;
    private final ImageUploadService imageUploadService;
    private final AnswerImageService answerImageService;

    public AnswerService(
            AnswerRepository answerRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            AnswerReportRepository answerReportRepository,
            AnswerCommentRepository answerCommentRepository,
            ImageUploadService imageUploadService, AnswerImageService answerImageService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.answerReportRepository = answerReportRepository;
        this.answerCommentRepository = answerCommentRepository;
        this.imageUploadService = imageUploadService;
        this.answerImageService = answerImageService;
    }

    @Transactional
    @CacheEvict(value = "questions", key = "#a0") // 直接取 questionId
    public Answer createAnswer(Long questionId, Long userId, LocalDateTime answerTime, String content) throws IOException {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));


        Answer answer = new Answer();
        answer.setQuestion(question);
        answer.setAuthor(user);
        answer.setCreatedTime(answerTime);

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
        answer.setContent(String.valueOf(result));

        Answer answerRes=  answerRepository.save(answer);
        // 提取content中所有img标签的src属性
        Pattern pattern2 = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher matcher2 = pattern2.matcher(content);
        while (matcher2.find()) {
            String imgUrl = matcher2.group(1);
            // 获取图片名称
            String imageName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
            imgUrl = imageUploadService.saveImage("uploads/temp_answer",imageName,"uploads/answer");
            // 创建QuestionImage对象并保存到数据库
            answerImageService.saveImage(answerRes.getId(),imgUrl);
        }

        return answerRes;
    }


    public boolean deleteReply(Long replyId, Long currentUserId) {
        // 先尝试删除回答
        Answer answer = answerRepository.findById(replyId).orElse(null);
        if (answer != null) {
            if (answer.getAuthor().getId().equals(currentUserId)) {
                answerRepository.delete(answer);
                return true;
            } else {
                return false;
            }
        }

        // 如果不是回答，尝试删除评论
        AnswerComment comment = answerCommentRepository.findById(replyId).orElse(null);
        if (comment != null) {
            if (comment.getUserId().equals(currentUserId)) {
                answerCommentRepository.delete(comment);
                return true;
            } else {
                return false;
            }
        }

        return false;
    }



    public String getAnswerContentById(Long answerId) {
        // 这里假设 Answer 类有一个 content 字段存储回答内容
        return answerRepository.findById(answerId)
                .map(Answer::getContent)
                .orElse(null);
    }
}