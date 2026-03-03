package com.charging.service;

import com.charging.entity.ChargingPile;
import com.charging.entity.Order;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.repository.OrderRepository;
import com.charging.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单服务
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> findByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo);
    }

    @Transactional
    public Order createFromReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        // 检查是否已有订单
        Optional<Order> existingOrder = orderRepository.findByReservationId(reservationId);
        if (existingOrder.isPresent()) {
            return existingOrder.get();
        }

        ParkingSpot spot = reservation.getSpot();
        ChargingPile pile = spot.getPile();

        // 计算实际使用时间
        LocalDateTime start = reservation.getActualArrivalTime() != null
                ? reservation.getActualArrivalTime()
                : reservation.getStartTime();
        LocalDateTime end = reservation.getActualLeaveTime() != null
                ? reservation.getActualLeaveTime()
                : reservation.getEndTime();

        // 1. 停车费：按小时计费（含峰谷动态定价）
        BigDecimal parkingFee = calculateDynamicFee(start, end, spot.getPricePerHour());

        // 2. 充电费（核心费用）：充电桩功率(kW) × 使用时长(小时) × 电费单价(元/度)
        BigDecimal chargingFee = BigDecimal.ZERO;
        BigDecimal electricityRate = spot.getServiceFee() != null
                ? spot.getServiceFee()
                : new BigDecimal("0.6"); // 默认电费 0.6 元/度
        if (pile != null && pile.getPower() != null) {
            long durationMinutes = java.time.Duration.between(start, end).toMinutes();
            BigDecimal durationHours = BigDecimal.valueOf(durationMinutes)
                    .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);
            // 充电量(kWh) = 功率(kW) × 时长(小时)
            BigDecimal chargedKwh = pile.getPower().multiply(durationHours);
            // 充电费 = 充电量 × 电价
            chargingFee = chargedKwh.multiply(electricityRate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        // 3. 服务费（固定小额）
        BigDecimal serviceFee = new BigDecimal("0.80");

        // 总计 = 停车费 + 充电费 + 服务费
        BigDecimal totalAmount = parkingFee.add(chargingFee).add(serviceFee);

        // 生成订单号
        String orderNo = "ORD" + System.currentTimeMillis() +
                String.format("%04d", (int) (Math.random() * 10000));

        Order order = Order.builder()
                .orderNo(orderNo)
                .user(reservation.getUser())
                .reservation(reservation)
                .parkingFee(parkingFee)
                .chargingFee(chargingFee)
                .serviceFee(serviceFee)
                .totalAmount(totalAmount)
                .paymentStatus(0)
                .status(1) // 待支付
                .build();

        return orderRepository.save(order);
    }

    @Transactional
    public Order pay(Long orderId, String paymentMethod) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("订单已支付");
        }

        // 模拟支付逻辑及回调

        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(1);
        order.setPaymentTime(LocalDateTime.now());
        order.setStatus(2); // 已支付

        // 发送通知
        if (order.getUser() != null) {
            notificationService.sendSms(order.getUser().getPhone(), "订单 " + order.getOrderNo() + " 支付成功");
            notificationService.sendEmail(order.getUser().getEmail(), "支付成功通知", "您的订单已支付完成。");
        }

        return orderRepository.save(order);
    }

    @Transactional
    public Order complete(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() != 1) {
            throw new RuntimeException("订单未支付");
        }

        order.setStatus(3); // 已完成
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("已支付订单无法直接取消");
        }

        order.setStatus(0); // 已取消
        return orderRepository.save(order);
    }

    public Long countCompletedOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countCompletedOrdersBetween(start, end);
    }

    public BigDecimal calculateRevenue(LocalDateTime start, LocalDateTime end) {
        BigDecimal revenue = orderRepository.sumRevenueBetween(start, end);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    /**
     * 计算动态停车费用
     * 峰期 (08:00-10:00, 17:00-21:00): 1.5倍
     * 谷期 (23:00-07:00): 0.5倍
     * 平期 (其他): 1.0倍
     */
    private BigDecimal calculateDynamicFee(LocalDateTime start, LocalDateTime end, BigDecimal pricePerHour) {
        if (pricePerHour == null || start.isAfter(end)) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal pricePerMinute = pricePerHour.divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);

        LocalDateTime current = start;
        while (current.isBefore(end)) {
            int hour = current.getHour();
            BigDecimal multiplier = BigDecimal.ONE;

            // 峰期
            if ((hour >= 8 && hour < 10) || (hour >= 17 && hour < 21)) {
                multiplier = BigDecimal.valueOf(1.5);
            }
            // 谷期
            else if (hour >= 23 || hour < 7) {
                multiplier = BigDecimal.valueOf(0.5);
            }

            totalFee = totalFee.add(pricePerMinute.multiply(multiplier));
            current = current.plusMinutes(1);
        }

        // 最低收费（至少15分钟的平期价格）
        BigDecimal minimumFee = pricePerHour.divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP);
        if (totalFee.compareTo(minimumFee) < 0) {
            return minimumFee;
        }

        return totalFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
