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
 * 给个人普通用户的 “我的个人中心” 控制台接口
 * 
 * 作用：主要负责对本身属性资料包（账号密、手机、车牌号等）展开管理修缮的操作提供落地点。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户C端自我管理", description = "只允许用户修修补补查阅自己的一亩三分地资料用的口子")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "进入【我的】界面时，提取本人最全的私有明细报文资料库")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            // 这里能做到这么优雅无感提取是因为在前置的 AuthTokenFilter 中
            // 早就帮你把从 Http-Header 获取的串化成了 Java 可以识别的 Principal 对象丢线程池上下文了
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("未匹配到合法人户信息"));

        // 手工精简并挑拣部分脱敏（不包含 password 重磅加密核弹密码盐等信息）的散件组装发送出去保障安全
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("carPlate", user.getCarPlate()); // 核心：绑定充费和入库用的车牌号
        userInfo.put("realName", user.getRealName()); // 防骗子实名校验名
        userInfo.put("role", user.getRole()); // 用来判定给不给前端点亮"返回管理员后台仪表板"特殊菜单的开关
        userInfo.put("status", user.getStatus());
        userInfo.put("createdAt", user.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @PutMapping("/me")
    @Operation(summary = "重写更新本人脱敏开放修改的基础档案属性")
    public ResponseEntity<ApiResponse<User>> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody User updateData) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("无牌幽灵账户"));

        User updated = userService.update(user.getId(), updateData);
        return ResponseEntity.ok(ApiResponse.success("资料换牌更新成功！", updated));
    }

    @PutMapping("/me/password")
    @Operation(summary = "独立隔离的敏感防线口：核查旧密码校验后更换新的大门密码钥匙")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> passwords) { // 专防 Json 送过来两串旧和新 {oldPassword:x, newPassword:y}
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("无效黑户"));

        String oldPassword = passwords.get("oldPassword");
        String newPassword = passwords.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            throw new RuntimeException("你总得把原来的锁长啥样或者换新锁的样子填一填吧！");
        }

        userService.updatePassword(user.getId(), oldPassword, newPassword);
        // 为了安全建议返回空体结构，强迫移动端去登录页重签
        return ResponseEntity.ok(ApiResponse.success("密码换心成功完成", null));
    }
}
