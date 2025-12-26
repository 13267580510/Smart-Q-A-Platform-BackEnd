package org.example.backend.controller;

import org.example.backend.dto.AdminUsersDTO;
import org.example.backend.dto.PageResponse;
import org.example.backend.dto.UserDTO;
import org.example.backend.model.User;
import org.example.backend.model.UserRole;
import org.example.backend.repository.AnswerRepository;
import org.example.backend.repository.QuestionRepository;
import org.example.backend.service.UserService;
import org.example.backend.utils.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    private final UserService userService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    public AdminUserController(UserService userService, QuestionRepository questionRepository, AnswerRepository answerRepository) {
        this.userService = userService;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * 查询所有用户信息（支持分页和按状态筛选）
     * @param page 页码，默认值为 0
     * @param size 每页数量，默认值为 10
     * @param status 用户状态，可选参数
     * @return ApiResponse
     */
    @GetMapping("/users")
    public ApiResponse getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "username", required = false) String username // 新增username参数
    ) {
        System.out.println("开始执行");
        Pageable pageable = PageRequest.of(page-1, size);

        Page<User> userPage;
        if (status != null && username != null) {
            System.out.println("组合查询");
            // 同时存在status和username时，按两者组合查询（示例：先状态后用户名模糊）
            userPage = userService.getUsersByStatusAndUsername(status, username, pageable);
        } else if (status != null) {
            System.out.println("仅状态查询");
            // 仅存在status时，按状态查询
            userPage = userService.getUsersByStatus(status, pageable);
        } else if (username != null && username.length() >= 1) { // 要求username至少2字符
            System.out.println("仅用户查询");
            // 仅存在username且长度≥2时，按用户名模糊查询
            userPage = userService.getUsersByUsernameLike(username, pageable);
            System.out.println(userPage.isEmpty()?"空":"非空");
        } else {
            System.out.println("无条件查询");

            // 无筛选条件时，查询所有用户
            userPage = userService.getAllUsers(pageable);
        }

        Page<AdminUsersDTO> userDTOPage = userPage.map(user -> AdminUsersDTO.fromUser(user, questionRepository, answerRepository));
        PageResponse<AdminUsersDTO> pageResponse = PageResponse.fromPage(userDTOPage);
        return ApiResponse.success(200, "查询用户信息成功", pageResponse);
    }
    /**
     * 创建管理员用户
     * @param user 用户信息
     * @return ApiResponse
     */
    @PostMapping("/Administator")
    public ApiResponse createAdminUser(@RequestBody User user) {
        user.setRole(UserRole.ADMIN);
        User createdUser = userService.register(user);
        UserDTO userDTO = UserDTO.fromUser(createdUser, questionRepository, answerRepository);
        return ApiResponse.success(201, "管理员用户创建成功", userDTO);
    }

    /**
     * 删除用户
     * @param userId 用户 ID
     * @return ApiResponse
     */
    @DeleteMapping("/delete/{userId}")
    public ApiResponse deleteUser(@PathVariable("userId") Long userId) {
        try {
            userService.deleteUser(userId);
            return ApiResponse.success(200, "用户删除成功");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }


    /**
     * 修改用户密码
     * @param userId 用户 ID
     * @param request 请求体
     * @return ApiResponse
     */
    @PutMapping("/update/{userId}/password")
    public ApiResponse changeUserPassword(@PathVariable("userId") Long userId,
                                          @RequestBody Map<String, Object> request) {
        try {
            // 调用服务层方法修改密码
            return  userService.changePassword(userId,request);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}