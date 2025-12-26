package org.example.backend.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtUtils {
    private static final String KEY = "nfu";

    // 生成JWT，确保权限以正确格式存储
    public static String generateToken(Long userId, String username, List<String> authorities) {
        JWTCreator.Builder builder = JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 86400000));

        // 确保权限列表不为null且包含ROLE_前缀
        if (authorities != null && !authorities.isEmpty()) {
            List<String> normalizedAuthorities = authorities.stream()
                    .map(authority -> {
                        // 确保权限以ROLE_开头
                        if (authority != null && !authority.startsWith("ROLE_")) {
                            return "ROLE_" + authority;
                        }
                        return authority;
                    })
                    .collect(Collectors.toList());

            System.out.println("生成Token的权限列表: " + normalizedAuthorities);
            builder.withClaim("authorities", normalizedAuthorities);
        } else {
            // 如果没有提供权限，使用默认的USER角色
            System.out.println("使用默认权限: ROLE_USER");
            builder.withClaim("authorities", Collections.singletonList("ROLE_USER"));
        }

        return builder.sign(Algorithm.HMAC256(KEY));
    }

    // 解析JWT，增加类型安全检查
    public static Map<String, Object> parseToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(KEY))
                    .build()
                    .verify(token)
                    .getClaims()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().as(Object.class)
                    ));
        } catch (JWTVerificationException e) {
            // 记录详细的错误信息
            System.err.println("JWT验证失败: " + e.getMessage());
            throw e;
        }
    }


    // 获取用户ID（从token中提取）
    public static Long getUserIdFromToken(String token) {
        System.out.println("从token中拿ID");
        Map<String, Object> claims = parseToken(token);
        // 假设token中包含userId字段，类型为Long
        Object userId = claims.get("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        } else if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return null;
    }

    // 检查用户是否具有管理员角色
    public static boolean isUserAdmin(String token) {
        Map<String, Object> claims = parseToken(token);
        Object authoritiesObj = claims.get("authorities");
        if (authoritiesObj instanceof List<?>) {
            List<?> authorities = (List<?>) authoritiesObj;
            for (Object auth : authorities) {
                if (auth instanceof String && "ROLE_ADMIN".equals(auth)) {
                    return true;
                }
            }
        }
        return false;
    }
}