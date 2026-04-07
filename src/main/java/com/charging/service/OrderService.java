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
 * 订单计费与支付服务
 *
 * 作用：处理订单生成、计费、支付和状态流转。
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

    /**
     * 根据预约记录生成订单
     */
    @Transactional
    public Order createFromReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        Optional<Order> existingOrder = orderRepository.findByReservationId(reservationId);
        if (existingOrder.isPresent()) {
            return existingOrder.get();
        }

        ParkingSpot spot = reservation.getSpot();
        ChargingPile pile = spot.getPile();

        LocalDateTime start = reservation.getActualArrivalTime() != null
                ? reservation.getActualArrivalTime()
                : reservation.getStartTime();
        LocalDateTime end = reservation.getActualLeaveTime() != null
                ? reservation.getActualLeaveTime()
                : reservation.getEndTime();

        BigDecimal parkingFee = calculateDynamicFee(start, end, spot.getPricePerHour());

        BigDecimal chargingFee = BigDecimal.ZERO;
        BigDecimal electricityRate = spot.getServiceFee() != null
                ? spot.getServiceFee()
                : new BigDecimal("1.0");

        if (pile != null && pile.getPower() != null) {
            long durationMinutes = java.time.Duration.between(start, end).toMinutes();
            BigDecimal durationHours = BigDecimal.valueOf(durationMinutes)
                    .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);

            BigDecimal chargedKwh = pile.getPower().multiply(durationHours);

            chargingFee = chargedKwh.multiply(electricityRate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal serviceFee = new BigDecimal("0.80");

        BigDecimal totalAmount = parkingFee.add(chargingFee).add(serviceFee);

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
                .status(1)
                .build();

        return orderRepository.save(order);
    }

    /**
     * 支付订单
     */
    @Transactional
    public Order pay(Long orderId, String paymentMethod) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("订单已支付");
        }

        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(1);
        order.setPaymentTime(LocalDateTime.now());
        order.setStatus(2);

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
            throw new RuntimeException("订单未支付，无法完成");
        }

        order.setStatus(3);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("已支付订单不能直接取消");
        }

        order.setStatus(0);
        return orderRepository.save(order);
    }

    /**
     * 统计指定时间段内的已完成订单数量
     */
    public Long countCompletedOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countCompletedOrdersBetween(start, end);
    }

    /**
     * 统计指定时间段内的营业收入
     */
    public BigDecimal calculateRevenue(LocalDateTime start, LocalDateTime end) {
        BigDecimal revenue = orderRepository.sumRevenueBetween(start, end);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    /**
     * 按时段计算停车费用
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

            if ((hour >= 8 && hour < 10) || (hour >= 17 && hour < 21)) {
                multiplier = BigDecimal.valueOf(1.5);
            }
            else if (hour >= 23 || hour < 7) {
                multiplier = BigDecimal.valueOf(0.5);
            }

            totalFee = totalFee.add(pricePerMinute.multiply(multiplier));
            current = current.plusMinutes(1);
        }

        BigDecimal minimumFee = pricePerHour.divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP);
        if (totalFee.compareTo(minimumFee) < 0) {
            return minimumFee;
        }

        return totalFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
