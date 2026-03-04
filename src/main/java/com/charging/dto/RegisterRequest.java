package com.charging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求 DTO (Data Transfer Object)
 * 
 * 作用：接收前端发送的新用户注册表单信息，通过 Spring Validation (JSR-380)
 * 自动拦截长度过短、为空等非法恶意数据请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * 用户希望注册的登录名
     * 必需提供，且长度必须在 3 到 50 个字符之间
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    private String username;

    /**
     * 用户设定的密码 (前端传输通常不应该是明文)
     * 必需提供，为了保证最低强度限制，密码需至少 6 个字符长
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    private String password;

    /**
     * 用户的电话号码
     * 可选项。如果是手机号注册则也可以加类似 @Pattern 的正则匹配
     */
    private String phone;

    /**
     * 用户的电子邮件地址，可用于找回密码相关的业务
     */
    private String email;

    /**
     * 用户绑定的默认车牌号码 (例如："京A88888")
     * 非常重要，可以和场站的自动道闸摄像头联动识别，免密通过
     */
    private String carPlate;

    /**
     * 用户的真实姓名，在一些实名制计费要求中需要使用
     */
    private String realName;
}
