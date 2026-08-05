package com.example.project.config;

import com.example.project.common.ErrorCode;
import com.example.project.common.Result;
import com.example.project.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security配置
 * Swagger UI 和 OpenAPI文档路径放行
 * 用户管理接口 ADMIN-only（注意：/api/users/** 不匹配 /api/users 本身，必须显式配置）
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()       // 注册公开
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")    // 列表 ADMIN-only（/api/users/** 不匹配 /api/users 本身）
                        .requestMatchers("/api/users/**").hasRole("ADMIN")                 // 详情/更新/状态/删除/游标
                        // 商品管理：写操作 ADMIN-only，GET 走 anyRequest().authenticated()（登录即可）
                        // 注意：/api/products/** 不匹配 /api/products 本身（POST 建商品是精确路径），必须显式加一条
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        // 优惠券：管理员建券模板；领券/我的券/可领列表登录即可
                        .requestMatchers(HttpMethod.POST, "/api/coupons").hasRole("ADMIN")
                        // 秒杀：管理端 ADMIN-only（/api/seckill/admin/**）；用户抢购/查询走 anyRequest().authenticated()
                        .requestMatchers("/api/seckill/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, e) -> writeJson(res, 401, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> writeJson(res, 403, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 以统一 Result 格式输出 401/403 响应
     */
    private void writeJson(jakarta.servlet.http.HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.error(errorCode)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
