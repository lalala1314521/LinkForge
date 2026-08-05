package com.example.project.security;

import com.example.project.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT认证过滤器（每次清求执行一次）
 * 将userId加入MDC上下文， 与 TraceId联动
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if(StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);
            //旧 token 可能无 role claim，兜底为普通用户
            if(!StringUtils.hasText(role)) {
                role = "USER";
            }
            //将userId 写入 MDC ，使日志自动携带用户信息
            MDC.put("userId", String.valueOf(userId));

            try {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                auth.setDetails(userId); //details 存储 userId, 供 SecurityUtil 取用
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
            } finally {
                //确保清求结束后清楚MDC，防止线程复用是数据泄露到后续清求
                MDC.remove("userId");
            }
        } else {
            filterChain.doFilter(request, response);
        }

    }
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if(StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

}