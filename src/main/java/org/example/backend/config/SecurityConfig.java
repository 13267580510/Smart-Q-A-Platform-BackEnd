package org.example.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.example.backend.filter.JwtAuthenticationFilter;
import org.example.backend.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, UserService userService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userService = userService;
    }

    private final UserService userService;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.out.println("=== AuthenticationEntryPoint 被调用 ===");
                            System.out.println("请求URI: " + request.getRequestURI());
                            System.out.println("方法: " + request.getMethod());
                            System.out.println("认证异常: " + authException.getClass().getName());

                            // 如果请求的是 /error 路径，直接返回原始错误
                            if ("/error".equals(request.getRequestURI())) {
                                System.out.println("访问 /error 路径，直接返回");
                                // 这里可以尝试获取原始的响应状态码
                                Integer status = (Integer) request.getAttribute("javax.servlet.error.status_code");
                                if (status != null) {
                                    response.setStatus(status);
                                } else {
                                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                }
                                response.setContentType("application/json;charset=UTF-8");
                                try {
                                    String json = "{\"code\":401,\"message\":\"认证失败\"}";
                                    response.getWriter().write(json);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return;
                            }

                            // 原有的异常处理逻辑...
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            try {
                                String json = "{\"code\":401,\"message\":\"" + authException.getMessage() + "\"}";
                                response.getWriter().write(json);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            System.out.println("=== AccessDeniedHandler 被调用 ===");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            try {
                                String json = "{\"code\":403,\"message\":\"访问被拒绝\"}";
                                response.getWriter().write(json);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // 公开路径 - 必须包含 /error
                        .requestMatchers(
                                "/error",
                                "/api/auth/**",
                                "/uploads/**",
                                "/upload/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/questions",
                                "/api/debug/**"
                        ).permitAll()

                        // 测试：先暂时开放chat路径
                        .requestMatchers("/api/chat/**").permitAll()  // 临时开放测试

                        // ADMIN权限
                        .requestMatchers(
                                "/api/reports/{reportId}/ban",
                                "/api/reports/{userId}/unban",
                                "/api/reports/*",
                                "/api/admin/questions/**",
                                "/api/admin/answers/**"
                        ).hasRole("ADMIN")

                        // USER或ADMIN权限
                        .requestMatchers(
                                "/api/questions/{id}/like",
                                "/api/reports/**",
                                "/api/UserInfo/**",
                                "/api/admin/**",
                                "/api/chat/**"
                        ).hasAnyRole("USER", "ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOriginPatterns(List.of("*"));
                    config.setAllowedMethods(List.of("*"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }));

        return http.build();
    }
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}