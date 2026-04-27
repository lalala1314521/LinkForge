package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import  lombok.Data;
import org.springframework.security.core.parameters.P;

@Data
@Schema(description = "用户创建/注册请求")
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母，数字和下划线")
    @Schema(description = "用户名（3-50位字母数字下划线)", example = "newuser", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度为8-100个字符")
    @Pattern(regexp = "^(?=.*[A-Z])(?.*[a-z])(?=.*\\d).+$", message = "密码必须包含大写字母、小写字母和数字")
    @Schema(description = "密码(8-100位，需包含大小写字母和数字)", example = "NewPass123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 50, message = "昵称最多50个字符")
    @Schema(description = "昵称（可选，最多50个字符", example = "新用户")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号（中国大陆）", example = "13812345678")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

}