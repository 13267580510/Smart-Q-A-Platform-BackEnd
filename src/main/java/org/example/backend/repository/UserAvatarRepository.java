package org.example.backend.repository;

import org.example.backend.model.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Integer> {
    /**
     * 根据用户ID查找头像
     * @param userId 用户ID
     * @return 头像实体
     */
    Optional<UserAvatar> findByUserId(Long userId);

    /**
     * 删除指定用户的头像
     * @param userId 用户ID
     */
    void deleteByUserId(Long userId);
}