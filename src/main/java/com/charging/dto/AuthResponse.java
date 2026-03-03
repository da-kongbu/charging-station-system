package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String tokenType;
    private Long userId;
    private String username;
    private Integer role;
    private Integer creditScore;
    private String message;

    public static AuthResponse success(String token, Long userId, String username, Integer role, Integer creditScore) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(userId)
                .username(username)
                .role(role)
                .creditScore(creditScore)
                .message("登录成功")
                .build();
    }
}
