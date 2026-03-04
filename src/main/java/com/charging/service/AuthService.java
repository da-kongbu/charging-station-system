package com.charging.service;

import com.charging.dto.AuthResponse;
import com.charging.dto.LoginRequest;
import com.charging.dto.RegisterRequest;
import com.charging.entity.User;
import com.charging.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 账号认证与注册服务 (Service Layer)
 * 
 * 作用：处理用户登录、注册等与安全认证强相关的核心业务逻辑。
 * 它打通了 Spring Security 的 AuthenticationManager 和自定义的 JWT 签发工具类的闭环。
 */
@Service // 告诉 Spring 容器，这是一个 Service 业务处理类，交由 Spring 统一管理
@RequiredArgsConstructor // Lombok 提供：为类中所有带有 final 关键字的字段自动生成构造函数，实现构造器依赖注入
public class AuthService {

    // Spring Security 的身份认证核心接口（负责校验账密是否匹配）
    private final AuthenticationManager authenticationManager;
    // 自定义的 JWT 生成和解析工具类
    private final JwtTokenProvider jwtTokenProvider;
    // 自定义的用户业务服务，负责实际去数据库取用户数据
    private final UserService userService;

    /**
     * 处理用户登录请求
     *
     * @param request 包含前端传来的账号密码 DTO
     * @return 登录成功后生成的带有 Token 的前端响应结果 DTO
     */
    public AuthResponse login(LoginRequest request) {
        // 1. 委托 AuthenticationManager 执行实际的账密比对逻辑
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        // 2. 如果账密没报错，把认证成功的标记存入当前线程上下文 (SecurityContext)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. 再次查询数据库获取该用户的其他详细信息（如 ID、角色），用于组装给前端的返回值
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 4. 调用工具类生成 JWT 令牌
        String token = jwtTokenProvider.generateToken(authentication);

        // 5. 组合最终数据返回
        return AuthResponse.success(token, user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 处理新用户注册请求，并在注册后直接自动执行签发登录的流程
     *
     * @param request 包含详细注册信息（如密码、手机号、车牌等）的 DTO
     * @return 注册并自动登录成功后的 JWT 响应
     */
    public AuthResponse register(RegisterRequest request) {
        // 1. 调用底层的 UserService 将新用户落库（包含密码哈希加密）
        User user = userService.register(request);

        // 2. 刚刚写入数据库完毕，直接利用传来的明文账密再走一遍身份认证流程
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), // 用户名
                        request.getPassword())); // 原始明文密码（Spring Security 内部会自动拿它去跟数据库里刚存进去的密文比对）

        // 3. 签发登录 Token，做到“注册即登录”的用户体验
        String token = jwtTokenProvider.generateToken(authentication);

        return AuthResponse.success(token, user.getId(), user.getUsername(), user.getRole());
    }
}
