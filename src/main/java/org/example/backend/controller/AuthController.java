package org.example.backend.controller;

import org.example.backend.dto.UserDTO;
import org.example.backend.model.ResponseStatus;
import org.example.backend.model.User;
import org.example.backend.model.UserRole;
import org.example.backend.repository.AnswerRepository;
import org.example.backend.repository.QuestionImageRepository;
import org.example.backend.repository.QuestionRepository;
import org.example.backend.service.UserService;
import org.example.backend.utils.ApiResponse;
import org.example.backend.utils.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.GrantedAuthority;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    public AuthController(
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            UserService userService,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody User user) {
        try {
            System.out.println("接收到登录请求  "+"用户名:"+user.getUsername()+"密码："+user.getPassword());
            if (user.getUsername() == null || user.getPassword() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        "用户名或密码不能为空"
                ));
            }
            System.out.println("user.getUsername()："+user.getUsername()+"user.getPassword()"+user.getPassword());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
            );

            System.out.println("userDetail"+authentication.getPrincipal().toString());
            // 查询用户详细信息
            UserDetails userDetail = (UserDetails) authentication.getPrincipal();
            User userResponse = (User) userService.loadUserByUsername(user.getUsername());
            if(userResponse.getStatus().equals("ACTIVE")) {
                UserDTO userDTO = UserDTO.fromUser(userResponse,questionRepository,answerRepository);
                userResponse.setRole(UserRole.fromString(userResponse.getAuthorities().iterator().next().getAuthority()));
                System.out.println("查询结束");

                // 获取权限列表并转换为Spring Security格式（ROLE_前缀）
                List<String> authorities = userDetail.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList());

                // 使用改进后的JwtUtils生成JWT（直接传入用户名和权限）
                System.out.println("userDTO:"+userDTO.id());
                String token = jwtUtils.generateToken(userDTO.id(),userDTO.username(),authorities);
                System.out.println("用户登录成功");
                return ResponseEntity.ok(ApiResponse.success(
                        ResponseStatus.SUCCESS.getCode(),
                        "登录成功",
                        Map.of("data", userDTO, "token", token)
                ));
            }else{
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                        ResponseStatus.FORBIDDEN.getCode(),
                        "你的账号已被封禁，请联系管理员解封！"
                ));
            }
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                    ResponseStatus.UNAUTHORIZED.getCode(),
                    "密码或账号错误"
            ));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    ResponseStatus.FORBIDDEN.getCode(),
                    "账户已锁定"
            ));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    ResponseStatus.FORBIDDEN.getCode(),
                    "账户未激活"
            ));
        } catch (AuthenticationException e) {
            System.out.println("认证失败"+":"+e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                    ResponseStatus.UNAUTHORIZED.getCode(),
                    "认证失败"
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@RequestBody User user) {
        try {
            StringBuilder errorMessage = new StringBuilder();

            if (user.getUsername() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("用户名");
            }
            if (user.getPassword() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("密码");
            }
            if (user.getEmail() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("邮箱");
            }
            if (user.getAge() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("年龄");
            }
            if (user.getResidence() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("居住地");
            }
            if (user.getSex() == null) {
                if (!errorMessage.isEmpty()) {
                    errorMessage.append(", ");
                }
                errorMessage.append("性别");
            }


            if (!errorMessage.isEmpty()) {
                errorMessage.append("为必填项");
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        ResponseStatus.BAD_REQUEST.getCode(),
                        errorMessage.toString()
                ));
            }
        System.out.println("sex:"+user.getSex());

            User userRe =  userService.register(user);
            return ResponseEntity.ok(ApiResponse.success(
                    ResponseStatus.SUCCESS.getCode(),
                    "注册成功",
                    userRe.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ResponseStatus.BAD_REQUEST.getCode(),
                    e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ResponseStatus.INTERNAL_SERVER_ERROR.getCode(),
                    "注册失败，请稍后重试:"+e
            ));
        }
    }
}