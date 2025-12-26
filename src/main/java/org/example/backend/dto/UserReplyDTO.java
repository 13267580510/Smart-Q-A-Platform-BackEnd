package org.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserReplyDTO {
    private Long replyId;           // 回复ID（回答ID或评论ID）
    private String content;         // 回复内容
    private Long questionId;        // 问题ID
    private String questionTitle;   // 问题标题
    private String questionContent; // 问题内容
    private Long answerId;          // 所属回答ID（针对评论）
    private Long parentCommentId;   // 父评论ID（针对二级评论）
    private Boolean isComment;      // 是否为评论

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime; // 创建时间

    // 构造函数 - 用于回答
    public UserReplyDTO(Long replyId, String content, Long questionId, String questionTitle,
                        String questionContent, LocalDateTime createdTime) {
        this.replyId = replyId;
        this.content = content;
        this.questionId = questionId;
        this.questionTitle = questionTitle;
        this.questionContent = questionContent;
        this.isComment = false;
        this.createdTime = createdTime;
    }

    // 构造函数 - 用于评论
    public UserReplyDTO(Long replyId, String content, Long questionId, String questionTitle,
                        String questionContent, Long answerId, Long parentCommentId,
                        Boolean isComment, LocalDateTime createdTime) {
        this.replyId = replyId;
        this.content = content;
        this.questionId = questionId;
        this.questionTitle = questionTitle;
        this.questionContent = questionContent;
        this.answerId = answerId;
        this.parentCommentId = parentCommentId;
        this.isComment = isComment;
        this.createdTime = createdTime;
    }
}