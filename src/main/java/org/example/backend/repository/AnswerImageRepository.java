package org.example.backend.repository;

import jakarta.transaction.Transactional;
import org.example.backend.model.AnswerImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnswerImageRepository extends JpaRepository<AnswerImage, Long> {
    List<AnswerImage> findByAnswerId(Long answerId);

    // 根据图片ID删除图片
    @Transactional
    @Modifying
    @Query("DELETE FROM AnswerImage ai WHERE ai.id = :imageId")
    void deleteByImageId(Long imageId);

    // 根据问题ID删除图片
    @Transactional
    @Modifying
    @Query("DELETE FROM AnswerImage ai WHERE ai.answerId = :answerId")
    void deleteByAnswerId(Long answerId);
}