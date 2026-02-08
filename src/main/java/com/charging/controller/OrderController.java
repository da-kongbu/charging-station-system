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
 * 订单控制器
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "订单相关接口")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

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
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable(name = "id") Long id) {
        Order order = orderService.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/no/{orderNo}")
    @Operation(summary = "根据订单号查询订单")
    public ResponseEntity<ApiResponse<Order>> getOrderByNo(@PathVariable(name = "orderNo") String orderNo) {
        Order order = orderService.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/create/{reservationId}")
    @Operation(summary = "根据预约创建订单")
    public ResponseEntity<ApiResponse<Order>> createOrder(@PathVariable(name = "reservationId") Long reservationId) {
        Order order = orderService.createFromReservation(reservationId);
        return ResponseEntity.ok(ApiResponse.success("订单创建成功", order));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单")
    public ResponseEntity<ApiResponse<Order>> payOrder(
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> request) {
        String paymentMethod = request.getOrDefault("paymentMethod", "WECHAT");
        Order order = orderService.pay(id, paymentMethod);
        return ResponseEntity.ok(ApiResponse.success("支付成功", order));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(@PathVariable(name = "id") Long id) {
        Order order = orderService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("订单已取消", order));
    }
}
