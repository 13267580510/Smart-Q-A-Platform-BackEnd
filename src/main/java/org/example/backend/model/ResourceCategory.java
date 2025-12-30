package org.example.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "resource_category") // 与数据库表名一致
@DynamicInsert // 动态插入（只插入非空字段）
@DynamicUpdate // 动态更新（只更新修改的字段）
public class ResourceCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键（匹配表中id字段）
    private Long id;

    @Column(name = "category_name", nullable = false, length = 64) // 分类名称，非空
    private String categoryName;

    @Column(name = "create_time", nullable = false, updatable = false) // 创建时间，不可更新
    private LocalDateTime createTime;

    // 自动填充创建时间（无需手动设置，插入数据时自动生成）
    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }
}