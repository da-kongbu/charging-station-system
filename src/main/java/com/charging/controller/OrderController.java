package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.entity.Order;
import com.charging.entity.User;
import com.charging.service.OrderService;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单管理接口
 *
 * 作用：提供订单查询、支付和取消等接口。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "提供订单查询、支付和取消等接口")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    /**
     * 获取当前用户的订单列表
     */
    @GetMapping
    @Operation(summary = "获取当前用户的订单列表")
    public ResponseEntity<ApiResponse<List<Order>>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        List<Order> orders = orderService.findByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取订单详情")
    public ResponseEntity<ApiResponse<Order>> getOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Order order = orderService.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        // 越权校验：只能查看自己的订单
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权访问该订单"));
        }
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * 根据订单号查询订单
     */
    @GetMapping("/no/{orderNo}")
    @Operation(summary = "根据订单号查询订单")
    public ResponseEntity<ApiResponse<Order>> getOrderByNo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "orderNo") String orderNo) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Order order = orderService.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权访问该订单"));
        }
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * 根据预约生成订单
     */
    @PostMapping("/create/{reservationId}")
    @Operation(summary = "根据指定预约生成订单")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "reservationId") Long reservationId) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Order order = orderService.createFromReservation(reservationId);
        // 越权校验
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权操作"));
        }
        return ResponseEntity.ok(ApiResponse.success("订单生成成功", order));
    }

    /**
     * 支付订单
     */
    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单")
    public ResponseEntity<ApiResponse<Order>> payOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> request) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Order order = orderService.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权操作该订单"));
        }
        String paymentMethod = request.getOrDefault("paymentMethod", "WECHAT");
        Order paid = orderService.pay(id, paymentMethod);
        return ResponseEntity.ok(ApiResponse.success("支付成功", paid));
    }

    /**
     * 取消未支付订单
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消未支付订单")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Order order = orderService.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权操作该订单"));
        }
        Order cancelled = orderService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("订单已取消", cancelled));
    }
}
