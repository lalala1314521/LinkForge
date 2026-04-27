package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户信息更新请求DTO（不含密码，避免updata接口强制要求传密码）
 */
@Data
@Schema(description = "用户信息更细请求")
public class UserUpdateRequest {

    @Size(max = 50, message = "昵称最多50个字符")
    @Schema(description = "昵称（可选，最多50字符", example = "新昵称")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号（中国大陆格式）", example = "13812345678")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱", example = "user@example.com")
    private String email;
}