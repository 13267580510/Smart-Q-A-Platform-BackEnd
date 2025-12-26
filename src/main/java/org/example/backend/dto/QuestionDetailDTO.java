//QuestionDetailDTO.java
package org.example.backend.dto;

import org.example.backend.model.*;
import org.example.backend.repository.*;
import org.example.backend.service.UserService;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record QuestionDetailDTO(
        Long id,
        String title,
        String content,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        int viewCount,
        long categoryId,
        AuthorDTO author,
        List<AnswerDTO> answers,
        int likeCount, // 点赞数
        int dislikeCount, // 点踩数
        Boolean userVoteStatus, // 用户点踩或点赞过该问题，null表示未投票，true表示点赞，false表示点踩
        Long solvedAnswerId // 新增解决答案的 ID

) implements Serializable {
    public static QuestionDetailDTO fromQuestion(
            Question question,
            QuestionVoteRepository questionVoteRepository,
            UserService userService,
            QuestionImageRepository questionImageRepository,
            AnswerImageRepository   answerImageRepository,
            AnswerCommentRepository answerCommentRepository) {
        List<AnswerDTO> answerDTOS = question.getAnswers().stream()
                .map(answer -> AnswerDTO.fromAnswer(answer, answerImageRepository,answerCommentRepository,userService))
                .collect(Collectors.toList());
        // 从QuestionContent获取内容
        String content = question.getContent() != null ? question.getContent().getContent() : "";

        Boolean userVoteStatus = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
           User user = (User)userService.loadUserByUsername(authentication.getPrincipal().toString());
            if (user != null) {
                Optional<QuestionVote> vote = questionVoteRepository.findByUserIdAndQuestionId(user.getId(), question.getId());
                userVoteStatus = vote.map(QuestionVote::getVoteType).orElse(null);
            }
        }

        // 获取问题图片信息
        List<QuestionImage> questionImages = questionImageRepository.findByQuestionId(question.getId());
        Long solvedAnswerId = question.getIsSolved() != null ? question.getIsSolved().getId() : null;

        return new QuestionDetailDTO(
                question.getId(),
                question.getTitle(),
                content,
                question.getCreatedTime(),
                question.getUpdatedTime(),
                question.getViewCount(),
                question.getCategoryId(),
                new AuthorDTO(question.getAuthor().getId(), question.getAuthor().getUsername(), question.getAuthor().getNickname()),
                answerDTOS,
                question.getLikeCount(), // 设置点赞数
                question.getDislikeCount(), // 设置点踩数
                userVoteStatus,
                solvedAnswerId
        );
    }

    public record AnswerDTO(
            Long id,
            String content,
            LocalDateTime createdTime,
            Integer likeCount,
            AuthorDTO author,
            List<String> answerImageUrls, // 回答所有图片的 URL 列表
            List<CommentDTO> comments // 回答的评论列表
    )implements Serializable {
        public static AnswerDTO fromAnswer(Answer answer, AnswerImageRepository answerImageRepository, AnswerCommentRepository answerCommentRepository,UserService userService) {
            // 获取回答图片信息
            List<String> answerImageUrls = answerImageRepository.findByAnswerId(answer.getId()).stream()
                    .map(AnswerImage::getImagePath)
                    .collect(Collectors.toList());

            // 获取回答的一级评论
            List<AnswerComment> comments = answerCommentRepository.findByAnswerIdAndParentCommentIdIsNull(answer.getId());
            System.out.println("开始获取二级评论");
            List<CommentDTO> commentDTOS = comments.stream()
                    .map(comment -> CommentDTO.fromComment(comment, answerCommentRepository,userService))
                    .collect(Collectors.toList());
            return new AnswerDTO(
                    answer.getId(),
                    answer.getContent(),
                    answer.getCreatedTime(),
                    answer.getLikeCount(),
                    new AuthorDTO(answer.getAuthor().getId(), answer.getAuthor().getUsername(), answer.getAuthor().getNickname()),
                    answerImageUrls,
                    commentDTOS
            );
        }
    }

    public record CommentDTO(
            Long id,
            String content,
            LocalDateTime createdAt,
            AuthorDTO author,
            List<CommentDTO> childComments // 子评论列表
    )implements Serializable {
        public static CommentDTO fromComment(AnswerComment comment, AnswerCommentRepository answerCommentRepository, UserService userService) {
            // 获取子评论
            System.out.println("开始获取子评论");
            Optional<User> userOptional  = userService.findByIdReOPU(comment.getUserId());
            AuthorDTO author = userOptional .map(user -> new AuthorDTO(user.getId(), user.getUsername(), user.getNickname()))
                    .orElse(null);
            List<AnswerComment> childComments = answerCommentRepository.findByParentCommentId(comment.getId());
            List<CommentDTO> childCommentDTOS = childComments.stream()
                    .map(childComment -> CommentDTO.fromComment(childComment, answerCommentRepository,userService))
                    .collect(Collectors.toList());

            return new CommentDTO(
                    comment.getId(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    author,
                    childCommentDTOS
            );
        }
    }

    public record AuthorDTO(Long id, String username, String nickname) {}
}