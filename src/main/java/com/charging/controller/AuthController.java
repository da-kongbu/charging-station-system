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
 * 用户通行证(Passport)认证公开路由
 * 
 * 作用：这是整个应用为数不多的几个不设防、无需携带 JWT Token 即可敲门的接口群。
 * 专门处理最核心且必须对外的注册与登录换发身份信物的动作。
 */
@RestController // 声明为前后端分离架构的 REST 接口控制器
@RequestMapping("/api/auth") // 对外暴露在此 /api/auth 路由主干上
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "系统入口层：处理用户登录、开户注册分发票据")
public class AuthController {

    private final AuthService authService;

    /**
     * 【POST】受理用户名密码查验登录请求
     * 
     * @Valid 注解：搭配 LoginRequest 里写的 @NotBlank 起作用。一旦 JSON 反序列化后发现字段为空格，
     *        Controller 会自动抛出
     *        MethodArgumentNotValidException，随后被全局异常拦截器接管拦下，根本不会执行里面代码。
     */
    @PostMapping("/login")
    @Operation(summary = "用户密码登录", description = "使用注册时的用户名和哈希密码获取准入JWT。")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        // 调用底层鉴权核心业务逻辑
        AuthResponse response = authService.login(request);
        // 上卷一层统一大包装返回
        return ResponseEntity.ok(ApiResponse.success("欢迎回来，登录成功！", response));
    }

    /**
     * 【POST】受理新用户提交表格开户注册请求
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册投档", description = "开立新账户同时免费附送一次直接登录换票。")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("🎉系统入驻注册成功啦！", response));
    }
}
