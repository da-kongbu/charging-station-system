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
 * C端我的订单控制器
 * 
 * 作用：为个人车主提供查阅自己花钱记录、并提供模拟吊起收银台付钱功能的接口网关。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单收银管理", description = "服务于 C 端车主的个人订单详情账单操作")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    /**
     * 【GET】拉取我的历史消费记录本
     * 
     * @AuthenticationPrincipal：通过 Spring Security 的上下文，优雅地直接把解析好的 Token 里的车主本体提取出来
     */
    @GetMapping
    @Operation(summary = "获取当前用户的全部订单账单明细列表")
    public ResponseEntity<ApiResponse<List<Order>>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails) { // 防翻墙窃取：只能提取自己名下的账单
        // 查库验明正身
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("读取身份令牌解析失败查无此人"));

        // 只给这一个用户的所有单子，按时间倒序排好返回
        List<Order> orders = orderService.findByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "点开某一条订单看完整花费明细")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable(name = "id") Long id) {
        Order order = orderService.findById(id)
                .orElseThrow(() -> new RuntimeException("查找的单据号可能存在异常，系统里没有此底子"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * 【GET】专为第三方回调设计的查询接口，按业务里的字符串流水单号查询
     */
    @GetMapping("/no/{orderNo}")
    @Operation(summary = "根据流水号(如ORDxxx)精准定位追查交易单")
    public ResponseEntity<ApiResponse<Order>> getOrderByNo(@PathVariable(name = "orderNo") String orderNo) {
        Order order = orderService.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * 【POST】一般是由 Reservation 结束时候在后台自旋发起，这里也留了被前端强行触发找补防丢单的后门
     */
    @PostMapping("/create/{reservationId}")
    @Operation(summary = "(系统防丢后备机制)根据指定的历史预约凭证计算生成收费派单")
    public ResponseEntity<ApiResponse<Order>> createOrder(@PathVariable(name = "reservationId") Long reservationId) {
        Order order = orderService.createFromReservation(reservationId);
        return ResponseEntity.ok(ApiResponse.success("账单切结清算成功", order));
    }

    /**
     * 【POST】核心虚拟收银台，前端调它来把状态从待支付变成已支付
     */
    @PostMapping("/{id}/pay")
    @Operation(summary = "拉起第三方收银台完成模拟入金支付流")
    public ResponseEntity<ApiResponse<Order>> payOrder(
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> request) { // 可以接受 payload 传 {"paymentMethod": "WECHAT/ALIPAY"}

        // 解析传上来的支付方式，兜底走微信
        String paymentMethod = request.getOrDefault("paymentMethod", "WECHAT");
        Order order = orderService.pay(id, paymentMethod); // 呼叫服务层真正把状态转了抛短信

        return ResponseEntity.ok(ApiResponse.success("支付成功，多谢款待", order));
    }

    /**
     * 【POST】客户后悔药（实际情况肯定有退款审核等限制）
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "车主单方面尝试流标这笔未付款账单")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(@PathVariable(name = "id") Long id) {
        Order order = orderService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("已取消该笔尚未发生成交的定单", order));
    }
}
