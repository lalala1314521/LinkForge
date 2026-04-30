package com.example.project.service;

import com.example.project.common.PageResult;
import com.example.project.dto.request.LoginRequest;
import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.request.UserQueryRequest;
import com.example.project.dto.request.UserUpdateRequest;
import com.example.project.dto.response.LoginResponse;
import com.example.project.dto.response.UserResponse;
import com.example.project.enums.UserStatus;

/**
 * 用户服务接口
 */
public interface UserService {

    LoginResponse login(LoginRequest request);

    Long createUser(UserCreateRequest request);

    UserResponse getUserById(Long id);

    PageResult<UserResponse> queryUser(UserQueryRequest request);

    void updateUserInfo(Long id, UserUpdateRequest request);

    void updateUserStatus(Long id, UserStatus status);

    void deleteUser(Long id);
}