package org.example.backend.repository;

import org.example.backend.model.QuestionVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionVoteRepository extends JpaRepository<QuestionVote, Long> {
    // 根据用户ID和问题ID查询投票记录
    Optional<QuestionVote> findByUserIdAndQuestionId(Long userId, Long questionId);
    // 统计问题的点赞数
    @Query("SELECT COUNT(v) FROM QuestionVote v WHERE v.question.id = :questionId AND v.voteType = true")
    int countLikesByQuestionId(@Param("questionId") Long questionId);

    // 统计问题的点踩数
    @Query("SELECT COUNT(v) FROM QuestionVote v WHERE v.question.id = :questionId AND v.voteType = false")
    int countDislikesByQuestionId(@Param("questionId") Long questionId);
}