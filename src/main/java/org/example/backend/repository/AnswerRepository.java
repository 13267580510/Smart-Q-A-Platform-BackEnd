package org.example.backend.repository;

import org.example.backend.model.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Integer countByAuthor_Id(Long userId);
    Page<Answer> findByAuthor_Id(Long userId, Pageable pageable);
    List<Answer> findByAuthor_Id(Long userId);

    // 查询用户的一级回答（parentAnswer为null）
    // 查询用户的二级评论（parentAnswer不为null）
}