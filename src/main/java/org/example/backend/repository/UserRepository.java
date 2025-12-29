package org.example.backend.repository;

import org.example.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);


    /**
     * 根据用户状态查找用户（分页）
     * @param status 用户状态
     * @param pageable 分页信息
     * @return 用户分页信息
     */
    Page<User> findByStatus(String status, Pageable pageable);

    // 根据状态查询用户
    // 按用户名模糊查询（忽略大小写）
    @Query("SELECT u FROM User u WHERE u.username LIKE CONCAT('%', :username, '%')")
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    // 组合查询（状态+用户名模糊）
    Page<User> findByStatusAndUsernameContainingIgnoreCase(
            @Param("status") String status,
            @Param("username") String username,
            Pageable pageable
    );

    // 自定义查询示例（与上面方法等效）
    @Query("SELECT u FROM User u WHERE u.status = :status AND LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    Page<User> searchByStatusAndUsername(
            @Param("status") String status,
            @Param("username") String username,
            Pageable pageable
    );

    List<User> findByRole(User.UserRole userRole);
}