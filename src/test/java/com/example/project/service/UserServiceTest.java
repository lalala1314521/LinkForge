package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.LoginRequest;
import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.request.UserQueryRequest;
import com.example.project.dto.response.LoginResponse;
import com.example.project.dto.response.UserResponse;
import com.example.project.entity.User;
import com.example.project.enums.UserRole;
import com.example.project.enums.UserStatus;
import com.example.project.mapper.UserMapper;
import com.example.project.service.impl.UserServiceImpl;
import com.example.project.util.JwtUtil;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * UserService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务单元测试")
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock ApplicationEventPublisher eventPublisher;  // 任务4: Mock 事件发布器

    @InjectMocks UserServiceImpl userService;

    // ---- 测试数据构造 ----

    private User buildUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("encoded_password");
        u.setNickname("昵称_" + username);
        u.setPhone("13812348000");
        u.setEmail("admin@example.com");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    // ---- createUser ----

    @Test
    @DisplayName("创建用户成功 - 返回用户ID、角色默认USER并发布事件")
    void createUser_success() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("Pass@123");

        given(userMapper.existsByUsername("newuser")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");

        // 模拟 insert 设置 ID（void 方法需用 doAnswer）
        org.mockito.Mockito.doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return null;
        }).when(userMapper).insert(any(User.class));

        Long id = userService.createUser(req);

        BDDAssertions.then(id).isEqualTo(100L);
        // 注册用户角色强制为 USER，不允许自提权
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userMapper).should().insert(userCaptor.capture());
        BDDAssertions.then(userCaptor.getValue().getRole()).isEqualTo(UserRole.USER);
        // 任务4: 验证事件被发布
        then(eventPublisher).should().publishEvent(any());
    }

    @Test
    @DisplayName("创建用户失败 - 用户名重复抛出 BusinessException")
    void createUser_duplicateUsername_throwsException() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("admin");

        given(userMapper.existsByUsername("admin")).willReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    // ---- login ----

    @Test
    @DisplayName("登录成功 - 生成带角色 claim 的 Token")
    void login_success_generatesTokenWithRole() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Test@1234");

        User admin = buildUser(1L, "admin");
        admin.setRole(UserRole.ADMIN);

        given(userMapper.findByUsername("admin")).willReturn(admin);
        given(passwordEncoder.matches("Test@1234", "encoded_password")).willReturn(true);
        given(jwtUtil.generateToken(1L, "admin", "ADMIN")).willReturn("jwt-token");
        given(jwtUtil.getExpirationMs()).willReturn(86400000L);

        LoginResponse resp = userService.login(req);

        BDDAssertions.then(resp.getToken()).isEqualTo("jwt-token");
        BDDAssertions.then(resp.getUserId()).isEqualTo(1L);
        // Token 生成必须携带角色
        then(jwtUtil).should().generateToken(1L, "admin", "ADMIN");
    }

    // ---- getUserById ----

    @Test
    @DisplayName("根据ID查询用户 - 命中数据库返回脱敏后的 UserResponse")
    void getUserById_found() {
        User user = buildUser(1L, "admin");
        given(userMapper.selectById(1L)).willReturn(user);

        UserResponse resp = userService.getUserById(1L);

        BDDAssertions.then(resp.getId()).isEqualTo(1L);
        BDDAssertions.then(resp.getUsername()).isEqualTo("admin");
        // 手机号/邮箱脱敏后返回
        BDDAssertions.then(resp.getPhone()).isEqualTo("138****8000");
        BDDAssertions.then(resp.getEmail()).isEqualTo("a***n@example.com");
        // 角色透出
        BDDAssertions.then(resp.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("根据ID查询用户 - 不存在抛出 USER_NOT_FOUND")
    void getUserById_notFound_throwsException() {
        given(userMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ---- deleteUser ----

    @Test
    @DisplayName("逻辑删除用户成功")
    void deleteUser_success() {
        given(userMapper.selectById(1L)).willReturn(buildUser(1L, "admin"));

        userService.deleteUser(1L);

        then(userMapper).should().logicalDelete(1L, UserStatus.DELETED);
    }

    // ---- queryUsers ----

    @Test
    @DisplayName("分页查询用户 - 返回 PageResult")
    void queryUsers_returnsPageResult() {
        UserQueryRequest req = new UserQueryRequest();
        req.setPage(1);
        req.setSize(10);

        given(userMapper.selectByCondition(any(), eq(0), eq(10)))
                .willReturn(List.of(buildUser(1L, "user1"), buildUser(2L, "user2")));
        given(userMapper.countByCondition(any())).willReturn(2L);

        PageResult<UserResponse> result = userService.queryUsers(req);

        BDDAssertions.then(result.getTotal()).isEqualTo(2L);
        BDDAssertions.then(result.getRecords()).hasSize(2);
    }
}
