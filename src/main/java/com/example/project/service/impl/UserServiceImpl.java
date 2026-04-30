package com.example.project.service.impl;

/**
 * 用户服务实现
 * 创建用户后发布UserCreatedEvent 触发异步处理
 */

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.LoginRequest;
import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.request.UserQueryRequest;
import com.example.project.dto.request.UserUpdateRequest;
import com.example.project.dto.response.LoginResponse;
import com.example.project.dto.response.UserResponse;
import com.example.project.entity.User;
import com.example.project.enums.UserStatus;
import com.example.project.event.UserCreatedEvent;
import com.example.project.mapper.UserMapper;
import com.example.project.service.UserService;
import com.example.project.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;
    //登录
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if(user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if(user.getStatus() == UserStatus.DISABLE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setToken(token);
        response.setExpiresIn(jwtUtil.getExpirationMs() / 1000);
        log.info("用户登录成功 : username = {}", user.getUsername());
        return response;
    }

    //创建用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateRequest request) {
        if(userMapper.existsByUsername(request.getUsername())){
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());

        userMapper.insert(user);
        log.info("用户创建成功：username={}", request.getUsername());

        eventPublisher.publishEvent(new UserCreatedEvent(this, user.getId(), user.getUsername(), user.getNickname()));

        return user.getId();
    }

    //查询用户
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userMapper.selectById(id);
        if(user == null){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserResponse> queryUsers(UserQueryRequest request) {
        int offset = (request.getPage() - 1) * request.getSize();
        List<User> users = userMapper.selectByCondition(request, offset,request.getSize());
        long total = userMapper.countByCondition(request);
        List<UserResponse> responses = users.stream().map(this::toResponse).toList();
        return new PageResult<>(responses, total, request.getPage(), request.getSize());
    }
    //更新用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user", key = "#id")
    public void updateUserInfo(Long id, UserUpdateRequest request) {
        User user = userMapper.selectById(id);
        if(user == null){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.updateInfo(id, request.getNickname(), request.getPhone(), request.getEmail());
        log.info("用户信息更新：id={}", id);
    }

    @Override
    @Transactional(rollbackFor= Exception.class)
    @CacheEvict(value = "user", key = "#id")
    public void updateUserStatus(Long id, UserStatus status) {
        User user = userMapper.selectById(id);
        if(user == null){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.updateStatus(id, status);
        log.info("用户状态更新 : id={}, status={}", id, status);
    }

    //删除用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user", key = "#id")
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if(user == null){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.logicalDelete(id, UserStatus.DELETED);
        log.info("用户逻辑删除：id={}", id);
    }



    //私有工具方法
    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}