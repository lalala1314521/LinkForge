package com.example.project.service;

import com.example.project.common.PageResult;
import com.example.project.dto.request.*;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.LoginResponse;
import com.example.project.dto.response.UserResponse;
import com.example.project.enums.UserStatus;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;

/**
 * 用户服务接口
 */
public interface UserService {

    LoginResponse login(LoginRequest request);

    Long createUser(UserCreateRequest request);

    UserResponse getUserById(Long id);

    PageResult<UserResponse> queryUsers(UserQueryRequest request);

    CursorPageResponse<UserResponse> queryUsersByCursor(CursorPageRequest request);

    void updateUserInfo(Long id, UserUpdateRequest request);

    void updateUserStatus(Long id, UserStatus status);

    void deleteUser(Long id);
}