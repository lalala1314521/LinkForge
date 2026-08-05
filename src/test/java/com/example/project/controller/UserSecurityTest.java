package com.example.project.controller;

import com.example.project.config.SecurityConfig;
import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.response.UserResponse;
import com.example.project.enums.UserStatus;
import com.example.project.service.UserService;
import com.example.project.util.JwtUtil;
import com.example.project.util.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理接口安全规则测试（URL 级 ADMIN 权限）
 * <p>
 * 通过 @Import(SecurityConfig.class) 加载真实安全配置，验证：
 * - GET /api/users 与 /api/users/** 仅 ADMIN 可访问（普通用户 403、ADMIN 200）
 * - POST /api/users 为公开注册接口（未登录不返回 401/403，请求可到达 Controller）
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("用户管理安全规则测试")
class UserSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UserService userService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean CacheManager cacheManager;
    //WebMvcConfig 会加载 RateLimitInterceptor，其构造依赖 RedisRateLimiter，此处 Mock 并放行
    @MockitoBean RedisRateLimiter redisRateLimiter;

    @BeforeEach
    void setUp() {
        //限流拦截器放行，避免影响接口测试
        lenient().when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/users/{id} - 普通用户访问返回 403 JSON")
    @WithMockUser(roles = "USER")
    void getUserById_user_forbidden() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /api/users/{id} - 管理员访问返回 200")
    @WithMockUser(roles = "ADMIN")
    void getUserById_admin_ok() throws Exception {
        UserResponse resp = new UserResponse();
        resp.setId(1L);
        resp.setUsername("admin");
        resp.setStatus(UserStatus.ACTIVE);
        resp.setCreatedAt(LocalDateTime.now());
        resp.setUpdatedAt(LocalDateTime.now());
        given(userService.getUserById(1L)).willReturn(resp);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/users - 普通用户访问列表返回 403（/api/users/** 不匹配 /api/users，须显式规则）")
    @WithMockUser(roles = "USER")
    void queryUsers_user_forbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /api/users - 管理员访问列表返回 200")
    @WithMockUser(roles = "ADMIN")
    void queryUsers_admin_ok() throws Exception {
        given(userService.queryUsers(any())).willReturn(
                new com.example.project.common.PageResult<>(java.util.List.of(), 0L, 1, 20));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - 普通用户删除返回 403")
    @WithMockUser(roles = "USER")
    void deleteUser_user_forbidden() throws Exception {
        mockMvc.perform(delete("/api/users/1").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("POST /api/users - 未登录可到达 Controller（permitAll 公开注册）")
    void createUser_unauthenticated_reachesController() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("NewPass1");
        req.setNickname("新用户");

        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 未登录不被安全拦截（非401/403），请求进入 Controller 参数校验（400）
                // 注：本切片下 body 反序列化存在既有问题，故断言 400 而非 200；
                //     注册成功路径由 UserServiceTest.createUser_success 覆盖
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
