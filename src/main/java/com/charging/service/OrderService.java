package com.charging.service;

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
import java.util.UUID;

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

        // 计算费用
        LocalDateTime start = reservation.getActualArrivalTime() != null
                ? reservation.getActualArrivalTime()
                : reservation.getStartTime();
        LocalDateTime end = reservation.getActualLeaveTime() != null
                ? reservation.getActualLeaveTime()
                : reservation.getEndTime();

        // 动态计费计算
        BigDecimal parkingFee = calculateDynamicFee(start, end, spot.getPricePerHour());

        BigDecimal serviceFee = spot.getServiceFee() != null ? spot.getServiceFee() : BigDecimal.ZERO;
        BigDecimal totalAmount = parkingFee.add(serviceFee);

        // 生成订单号
        String orderNo = "ORD" + System.currentTimeMillis() +
                String.format("%04d", (int) (Math.random() * 10000));

        Order order = Order.builder()
                .orderNo(orderNo)
                .user(reservation.getUser())
                .reservation(reservation)
                .parkingFee(parkingFee)
                .chargingFee(BigDecimal.ZERO)
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

        // 最低收费（至少1小时平期价格）
        if (totalFee.compareTo(pricePerHour) < 0) {
            return pricePerHour;
        }

        return totalFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
