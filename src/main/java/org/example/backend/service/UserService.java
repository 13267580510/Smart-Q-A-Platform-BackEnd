package org.example.backend.service;

import org.example.backend.dto.UserDTO;
import org.example.backend.dto.UserReplyDTO;
import org.example.backend.model.*;
import org.example.backend.repository.*;
import org.example.backend.utils.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final ImageUploadService uploadService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AnswerCommentRepository answerCommentRepository;
    private final AdminNotificationService adminNotificationService;
    public UserService(
            UserRepository userRepository,
            UserAvatarRepository userAvatarRepository,
            ImageUploadService uploadService,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            AnswerCommentRepository answerCommentRepository,
            AdminNotificationService adminNotificationService) {
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
        this.uploadService = uploadService;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.answerCommentRepository = answerCommentRepository;
        this.adminNotificationService = adminNotificationService;
    }


    public UserDTO updateUserInfo(Long userId, User user) {
        // 根据用户ID查找用户
        User existingUser = userRepository.findById(userId).orElse(null);
        if (existingUser != null) {
            if (user.getPassword()!=null){
                existingUser.setPassword(user.getPassword());
            }
            if (user.getNickname()!=null){
                existingUser.setNickname(user.getNickname());
            }
            if (user.getEmail()!=null){
                existingUser.setEmail(user.getEmail());
            }
            // 更新用户信息
            if (user.getIntroduction() != null) {
                existingUser.setIntroduction(user.getIntroduction());
            }
            if (user.getAge() != null) {
                existingUser.setAge(user.getAge());
            }
            if (user.getResidence() != null) {
                existingUser.setResidence(user.getResidence());
            }
            if(user.getSex()!=null){
                existingUser.setSex(user.getSex());
            }
            // 保存更新后的用户信息
            User userTemp =  userRepository.save(existingUser);
            UserDTO userDTO = UserDTO.fromUser(
                    userTemp,
                    questionRepository,
                    answerRepository
            );
            return userDTO;
        }
        return null;
    }


    public String uploadUserAvatar(Long userId, MultipartFile avatarFile) throws IOException {
        // 1. 查找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // 2. 上传头像文件
        String imageUrl = uploadService.uploadImage(avatarFile, "avatars");

        // 3. 创建或更新头像实体
        UserAvatar avatar = user.getUserAvatar();
        if (avatar == null) {
            // 创建新头像
            avatar = new UserAvatar();
            avatar.setUser(user); // 设置关联的用户
            user.setUserAvatar(avatar); // 双向关联
        }

        // 4. 更新头像信息
        avatar.setAvatarPath(imageUrl);

        // 5. 保存到数据库
        userAvatarRepository.save(avatar);
        return imageUrl;
    }

    public User register(User user) {
        //  检查用户名是否存在
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        //  检查邮箱是否存在
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        //  验证用户名格式 (长度6-20，仅包含字母、数字、下划线)
        if (!user.getUsername().matches("^[a-zA-Z0-9_]{6,20}$")) {
            throw new IllegalArgumentException("用户名长度需为6-20个字符，且仅包含字母、数字和下划线");
        }

        String password = user.getPassword();
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不足");
        } else if (!hasNumber || !hasLetter) {
            throw new IllegalArgumentException("必须包含字母和数字");
        }

        if (!user.getEmail().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        if (user.getSex()==null) {
            throw new IllegalArgumentException("性别不能为空");
        }

        user.setStatus("ACTIVE");
        user.setIntroduction("这个人很神秘，没有介绍");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("auth:" + auth.getAuthorities());
        System.out.println("auth信息："+auth);


        if (user.getRole() != null && user.getRole().equals(UserRole.ADMIN)) {
            boolean isAdmin = false;
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            for (GrantedAuthority authority : authorities) {
                if (authority.getAuthority().equals("ROLE_ADMIN")) {
                    isAdmin = true;
                    System.out.println("创建管理员用户");
                    break;
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("非管理员用户，无权创建");
            }
        } else {
            System.out.println("创建普通用户");
            user.setRole(UserRole.USER);
        }

        // 7. 保存用户
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("正在查询用户：" + username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        System.out.println(
                "找到用户：" + user.getUsername() +
                        " 加密密码长度：" + user.getPassword().length() +
                        "用户角色：" + user.getRole() +
                        "用户状态" + user.getStatus()
        );
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByIdReOPU(long userId) {
        return userRepository.findById(userId);
    }

    public User findByIdReUser(Long id) {
        Optional<User> user = userRepository.findById(id);

        return user.get();
    }

    public UserDTO findById(Long id) {
        Optional<User> user = userRepository.findById(id);
        UserDTO userDTO = null;
        if (user.isPresent()) {
            userDTO = UserDTO.fromUser(
                    user.get(),
                    questionRepository,
                    answerRepository
            );
        }
        return userDTO;
    }



    // 分页查询用户的所有回复（一级回答和二级评论）
    public Page<UserReplyDTO> getUserReplies(Long userId, Pageable pageable) {
        // 1. 查询用户的一级回答
        List<Answer> answers = answerRepository.findByAuthor_Id(userId);

        // 2. 查询用户的所有二级评论（不分页）
        List<AnswerComment> userComments = answerCommentRepository.findByUserId(userId);
        // 3. 合并结果
        List<UserReplyDTO> replies = new ArrayList<>();
        // 处理一级回答
        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            String questionContent = question.getContent() != null ? question.getContent().getContent() : null;

            replies.add(new UserReplyDTO(
                    answer.getId(),
                    answer.getContent(),
                    question.getId(),
                    question.getTitle(),
                    questionContent,
                    answer.getCreatedTime()
            ));
        }

        // 处理二级评论
        for (AnswerComment comment : userComments) {
            // 通过 answerId 查询关联的 Answer
            Optional<Answer> answerOptional = answerRepository.findById(comment.getAnswerId());

            if (answerOptional.isPresent()) {
                Answer answer = answerOptional.get();
                Question question = answer.getQuestion();
                String questionContent = question.getContent() != null ? question.getContent().getContent() : null;

                replies.add(new UserReplyDTO(
                        comment.getId(),
                        comment.getContent(),
                        question.getId(),
                        question.getTitle(),
                        questionContent,
                        answer.getId(),
                        comment.getParentCommentId(), // 直接使用 parentCommentId
                        true,
                        comment.getCreatedAt() // 使用 AnswerComment 的 createdAt 字段
                ));
            }
        }

        // 4. 按创建时间排序（最新的在前）
        replies = replies.stream()
                .sorted((r1, r2) -> r2.getCreatedTime().compareTo(r1.getCreatedTime()))
                .collect(Collectors.toList());

        // 5. 应用分页
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), replies.size());
        List<UserReplyDTO> pageContent = start <= end ? replies.subList(start, end) : Collections.emptyList();

        long totalElements = replies.size();

        return new PageImpl<>(pageContent, pageable, totalElements);
    }


    /**
     * 删除用户
     * @param userId 用户 ID
     */
    public void deleteUser(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            userRepository.deleteById(userId);
        } else {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    /**
     * 修改用户密码
     * @param userId 用户 ID
     * @param request 请求体
     */
    public ApiResponse changePassword(Long userId, Map<String, Object> request) {
        Optional<User> userOptional = userRepository.findById(userId);
        String newPassword = (String) request.get("newPassword");
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(newPassword)){
              return  ApiResponse.error(ResponseStatus.BAD_REQUEST.getCode(),"新密码与旧密码不能相同");
            }else{
                user.setPassword(newPassword);
                userRepository.save(user);
                return ApiResponse.success(ResponseStatus.SUCCESS.getCode(),"修改成功");
            }
        } else {
            throw new IllegalArgumentException("用户不存在");
        }
    }




    public Page<User> getUsersByStatus(String status, Pageable pageable) {
        return userRepository.findByStatus(status, pageable);
    }


    public Page<User> getUsersByUsernameLike(String username, Pageable pageable) {
        // 构建包含任意位置匹配的模糊查询条件
        String searchTerm = "%" + username + "%";
        Page<User> testPage= userRepository.findByUsernameContainingIgnoreCase(searchTerm, pageable);
        System.out.println("testPage"+testPage.getTotalElements());
        return testPage;
    }


    public Page<User> getUsersByStatusAndUsername(String status, String username, Pageable pageable) {
        String searchTerm = "%" + username + "%";
        return userRepository.findByStatusAndUsernameContainingIgnoreCase(status, searchTerm, pageable);
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }



    private void notifyUser(Long userId, String message) {
        // 这里可以实现具体的通知逻辑，如发送邮件、短信等
        adminNotificationService.publishNotificationToUser(userId, message);
    }
}

// 通知用户
