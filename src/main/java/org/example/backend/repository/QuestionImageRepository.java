package org.example.backend.repository;

import jakarta.transaction.Transactional;
import org.example.backend.model.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionImageRepository extends JpaRepository<QuestionImage, Long> {
    List<QuestionImage> findByQuestionId(Long questionId);
    // 根据图片ID删除图片
    void  deleteById(Long id);

    // 根据问题ID删除图片
    @Transactional
    @Modifying
    @Query("DELETE  FROM QuestionImage qi WHERE qi.questionId = :questionId")
    void deleteByQuestionId(Long questionId);

}