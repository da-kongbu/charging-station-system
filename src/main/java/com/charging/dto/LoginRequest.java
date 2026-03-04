package com.charging.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求 DTO (Data Transfer Object)
 * 
 * 作用：专门用于接收前端通过 Http POST 请求传递的用户登录凭证（账号和密码）。
 * 同时结合了 Jakarta Bean Validation（JSR 380）注解，在 Controller 层面实现快速阻断无效请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * 尝试登录的用户名
     * 
     * @NotBlank 注解的作用：
     *           确保接收到的字符串既不为 null，长度也必须大于 0（即去除头尾空格后不能为空串）。
     *           如果违反此规则，Controller 将抛出异常，并自动返回 message 定义的错误提示。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 尝试登录的用户密码（这里通常传输的应该是前端哈希过的密文或是明文由 HTTPS 保护）
     * 同样的，必须提供实际的字符。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
