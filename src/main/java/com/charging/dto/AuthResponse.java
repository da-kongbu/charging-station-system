package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应 DTO (Data Transfer Object)
 * 
 * 作用：当用户成功登录后，后端将通过这个对象向前端返回认证信息，
 * 包括用于后续请求的 JWT Token 及其所属的用户基本信息。
 */
@Data // 自动生成 Getter, Setter 等基础方法
@Builder // 提供链式调用的 Builder 模式
@NoArgsConstructor // 无参构造
@AllArgsConstructor // 全参构造
public class AuthResponse {

    /**
     * 用户的认证令牌 (JSON Web Token, JWT)
     * 登录成功后发给前端，前端需在后续请求中将其放入 Http Header 中携带
     */
    private String token;

    /**
     * 令牌类型，通常固定为 "Bearer" (HTTP 认证机制的一种标准类型)
     */
    private String tokenType;

    /**
     * 登录成功的用户数据库主键 ID
     */
    private Long userId;

    /**
     * 登录成功的用户名
     */
    private String username;

    /**
     * 用户的角色标识
     * (例如：0 表示普通用户，1 表示管理员。具体定义依赖业务约定)
     */
    private Integer role;

    /**
     * 登录结果的提示信息 (例如："登录成功")
     */
    private String message;

    /**
     * 静态工厂方法：快速构建一个成功登录的认证响应对象
     *
     * @param token    给用户签发的 JWT
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return 构造好的 AuthResponse 实例
     */
    public static AuthResponse success(String token, Long userId, String username, Integer role) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer") // 默认设置为 Bearer 类型
                .userId(userId)
                .username(username)
                .role(role)
                .message("登录成功")
                .build();
    }
}
