package org.example.backend.repository;

import org.example.backend.model.ResourceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceCategoryRepository extends JpaRepository<ResourceCategory, Long> {

    /**
     * 查询所有分类（按创建时间降序，最新的分类排在前面）
     * 由于表中无 status/deleted 字段，默认所有分类均为有效
     */
    List<ResourceCategory> findAllByOrderByCreateTimeDesc();

    /**
     * 检查分类是否存在（根据分类名称精确匹配）
     * 用于验证上传时的分类有效性
     */
    boolean existsByCategoryName(String categoryName);
}