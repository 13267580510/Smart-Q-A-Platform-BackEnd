// Question.java
package org.example.backend.model;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference // 被管理端
    private QuestionContent content; // 关联到QuestionContent

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;  // 改为 author


    @CreationTimestamp
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;


    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Answer> answers = new ArrayList<>();
    
    // 其他字段可以根据需要添加，如浏览量、点赞数等
    private int viewCount = 0;

    // 添加 status 属性
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionStatus status = QuestionStatus.PENDING;

    public enum QuestionStatus {
        NORMAL,PENDING,CLOSED
    }

    @Column(name = "category_id")
    //分类属性ID
    private Long categoryId;

    // 添加举报次数字段
    @Column(name = "report_count", columnDefinition = "INT DEFAULT 0")
    private Integer reportCount = 0;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<QuestionReport> reports = new ArrayList<>();

    // 添加点赞数和点踩数属性
    @Column(name = "like_count", columnDefinition = "INT DEFAULT 0")
    private Integer likeCount = 0;

    @Column(name = "dislike_count", columnDefinition = "INT DEFAULT 0")
    private Integer dislikeCount = 0;

    // 新增 is_solved 属性，关联到 Answer 的 id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "is_solved", referencedColumnName = "id")
    private Answer isSolved;

    public Question() {}

}