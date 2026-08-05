package com.example.project.controller;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController MockMvc 测试（校验 Controller 映射与 CRUD 行为）
 * <p>
 * 说明：
 * 1. @WebMvcTest 默认不加载应用自定义 SecurityConfig（走 Spring Security 默认规则），
 *    因此受保护接口统一用 @WithMockUser 提供认证；URL 级 ADMIN 权限规则由 UserSecurityTest 单独覆盖。
 * 2. POST /api/users 的 body 反序列化在本项目 @WebMvcTest 切片下存在既有问题
 *    （classpath 同时存在 Jackson 2 与 Jackson 3，切片 converter 产出空对象），
 *    该问题与本批次改动无关，故注册接口的成功路径由 UserServiceTest 覆盖。
 */
@WebMvcTest(UserController.class)
@DisplayName("用户Controller集成测试")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
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
    @DisplayName("GET /api/users/{id} - 查询用户详情成功")
    @WithMockUser(roles = "ADMIN")
    void getUserById_success() throws Exception {
        UserResponse resp = new UserResponse();
        resp.setId(1L);
        resp.setUsername("admin");
        resp.setStatus(UserStatus.ACTIVE);
        resp.setCreatedAt(LocalDateTime.now());
        resp.setUpdatedAt(LocalDateTime.now());

        given(userService.getUserById(1L)).willReturn(resp);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - 逻辑删除成功")
    @WithMockUser(roles = "ADMIN")
    void deleteUser_success() throws Exception {
        mockMvc.perform(delete("/api/users/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
