//JwtAuthenticationFilter.java
package org.example.backend.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.utils.JwtUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isPublicPath(String requestURI) {
        return requestURI.startsWith("/api/auth/") ||
                requestURI.startsWith("/api/uploads/") ||
                requestURI.startsWith("/api/upload/") ||
                requestURI.startsWith("/swagger-ui/") ||
                requestURI.startsWith("/v3/api-docs/") ||
                requestURI.startsWith("/error") ||  // 添加 /error 路径
                requestURI.equals("/api/questions") ||
                requestURI.equals("/api/chat/sse") ||
                requestURI.matches("/api/chat/session/.*/title"); // 这个路径可能需要认证
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        System.out.println("=== JWT过滤器开始 ===");
        System.out.println("请求URI: " + requestURI);
        System.out.println("请求方法: " + request.getMethod());

        // 1. 如果是公开路径，直接跳过
        if (isPublicPath(requestURI)) {
            System.out.println("跳过JWT验证，公开路径");
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        System.out.println("Authorization头: " + (authHeader != null ? "存在" : "null"));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("缺少有效的Authorization头，返回401");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "用户未登录");
            return;
        }

        try {
            String token = authHeader.substring(7);
            System.out.println("Token长度: " + token.length());

            Map<String, Object> claims = JwtUtils.parseToken(token);
            System.out.println("Token解析成功，claims keys: " + claims.keySet());

            String username = (String) claims.get("username");
            System.out.println("用户名: " + username);

            if (username == null || username.trim().isEmpty()) {
                throw new RuntimeException("Token中缺少用户名");
            }

            // 提取权限
            List<GrantedAuthority> authorities = extractAuthorities(claims);
            System.out.println("提取的权限: " + authorities);

            // 创建认证对象 - 使用包含权限列表的构造函数，这会自动设置为已认证状态
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            // 注意：这里不再需要调用 setAuthenticated(true)
            // 因为使用带有权限列表的构造函数会自动设置为已认证状态

            // 设置详细信息
            Map<String, Object> details = new HashMap<>();
            Object userId = claims.get("userId");
            if (userId != null) {
                details.put("userId", userId);
            }
            authentication.setDetails(details);

            // 设置到SecurityContext
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            System.out.println("认证设置完成: " + authentication);
            System.out.println("Principal: " + authentication.getPrincipal());
            System.out.println("Authorities: " + authentication.getAuthorities());
            System.out.println("Authenticated: " + authentication.isAuthenticated());
            System.out.println("SecurityContext设置成功");

            // 继续过滤器链
            filterChain.doFilter(request, response);

            System.out.println("过滤器链完成，响应状态: " + response.getStatus());

        } catch (Exception e) {
            System.err.println("JWT验证异常: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败: " + e.getMessage());
        }
    }

    private List<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Object authObj = claims.get("authorities");
        System.out.println("原始authorities对象: " + authObj);

        if (authObj instanceof List<?>) {
            List<?> authList = (List<?>) authObj;
            for (Object item : authList) {
                if (item instanceof String) {
                    String authority = (String) item;
                    // 确保有ROLE_前缀
                    if (!authority.startsWith("ROLE_")) {
                        authority = "ROLE_" + authority;
                    }
                    authorities.add(new SimpleGrantedAuthority(authority));
                }
            }
        }

        // 如果还是没有权限，添加默认权限
        if (authorities.isEmpty()) {
            System.out.println("没有找到权限，使用默认ROLE_USER");
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return authorities;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", status);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", System.currentTimeMillis());

        String json = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(json);
    }
}