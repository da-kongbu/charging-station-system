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
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String token = jwtTokenProvider.generateToken(authentication);

        return AuthResponse.success(token, user.getId(), user.getUsername(), user.getRole());
    }

    public AuthResponse register(RegisterRequest request) {
        User user = userService.register(request);

        // 注册后自动登录
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        String token = jwtTokenProvider.generateToken(authentication);

        return AuthResponse.success(token, user.getId(), user.getUsername(), user.getRole());
    }
}
