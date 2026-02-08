package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.entity.User;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息管理接口")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("carPlate", user.getCarPlate());
        userInfo.put("realName", user.getRealName());
        userInfo.put("role", user.getRole());
        userInfo.put("status", user.getStatus());
        userInfo.put("createdAt", user.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息")
    public ResponseEntity<ApiResponse<User>> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody User updateData) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        User updated = userService.update(user.getId(), updateData);
        return ResponseEntity.ok(ApiResponse.success("更新成功", updated));
    }

    @PutMapping("/me/password")
    @Operation(summary = "修改密码")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> passwords) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String oldPassword = passwords.get("oldPassword");
        String newPassword = passwords.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            throw new RuntimeException("请提供原密码和新密码");
        }

        userService.updatePassword(user.getId(), oldPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
    }
}
