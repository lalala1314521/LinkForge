package com.example.project.controller;

import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.response.UserResponse;
import com.example.project.enums.UserStatus;
import com.example.project.service.UserService;
import com.example.project.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController MockMvc 测试
 */
@WebMvcTest(UserController.class)
@DisplayName("用户Controller集成测试")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UserService userService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean CacheManager cacheManager;

    @Test
    @DisplayName("POST /api/users - 注册用户成功返回200")
    void createUser_success() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("NewPass1");
        req.setNickname("新用户");

        given(userService.createUser(any())).willReturn(1L);

        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @DisplayName("GET /api/users/{id} - 查询用户详情成功")
    @WithMockUser
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
    @WithMockUser
    void deleteUser_success() throws Exception {
        mockMvc.perform(delete("/api/users/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
