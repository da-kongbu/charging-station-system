package com.charging.controller;

import com.charging.dto.*;
import com.charging.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证公开接口
 *
 * 作用：提供无需登录即可访问的注册与登录接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "系统入口层：处理用户登录、开户注册分发票据")
public class AuthController {

    private final AuthService authService;

    /**
     * 处理用户名密码登录请求
     */
    @PostMapping("/login")
    @Operation(summary = "用户密码登录", description = "使用用户名和密码登录并获取 JWT。")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    /**
     * 处理用户注册请求
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新账户并返回登录结果。")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", response));
    }
}
