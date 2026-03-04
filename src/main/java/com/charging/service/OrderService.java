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
 * 作用：处理一次拔枪后带来的资金结算核心逻辑。
 * 它包含：动态阶梯停车费计算、峰谷电价充电费计算、模拟发起支付及状态流转等。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService; // 用于支付成功后发短信/邮件

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
     * 核心计费引擎：用一笔已经完成充电的预约记录，算账并生成一条扣费总单
     * <p>
     * 计费公式 = 动态停车费 + 实际充电耗电费 + 固定系统分润服务费
     * </p>
     */
    @Transactional // 开启事务，保证要生成单子就全部成功落地
    public Order createFromReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        // 幂等性检查：防抖止连点产生重复订单
        Optional<Order> existingOrder = orderRepository.findByReservationId(reservationId);
        if (existingOrder.isPresent()) {
            return existingOrder.get();
        }

        ParkingSpot spot = reservation.getSpot();
        ChargingPile pile = spot.getPile();

        // 计算账单跨度：判断是取真实的进出场物理打卡时间，还是兜底使用预约理论时间
        LocalDateTime start = reservation.getActualArrivalTime() != null
                ? reservation.getActualArrivalTime()
                : reservation.getStartTime();
        LocalDateTime end = reservation.getActualLeaveTime() != null
                ? reservation.getActualLeaveTime()
                : reservation.getEndTime();

        // 1. 停车费部分：私有私用 calculateDynamicFee 方法计算（有白天涨价黑夜便宜的逻辑）
        BigDecimal parkingFee = calculateDynamicFee(start, end, spot.getPricePerHour());

        // 2. 充电费部分（平台核心）：= 设备千瓦功率(kW) × 几小时(h) × 电费单价(元/度)
        BigDecimal chargingFee = BigDecimal.ZERO;
        // 如果此枪没单独设电价，全场兜底按平均电价 0.6 元
        BigDecimal electricityRate = spot.getServiceFee() != null
                ? spot.getServiceFee()
                : new BigDecimal("0.6");

        // 提取被充的硬设备参数，算出电表度数
        if (pile != null && pile.getPower() != null) {
            long durationMinutes = java.time.Duration.between(start, end).toMinutes();
            // 换算小时，保留四位小数免得丢精度
            BigDecimal durationHours = BigDecimal.valueOf(durationMinutes)
                    .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);

            // 累积耗电量(度)
            BigDecimal chargedKwh = pile.getPower().multiply(durationHours);

            // 乘电价得出这块的费用总额(2位小数)
            chargingFee = chargedKwh.multiply(electricityRate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        // 3. 平台抽水附加费（固定一笔象征性金额，比如服务器摊销）
        BigDecimal serviceFee = new BigDecimal("0.80");

        // 三费合一，得出需要前台扫码支付的总额
        BigDecimal totalAmount = parkingFee.add(chargingFee).add(serviceFee);

        // 生成给微信用的内部交易号流：ORD + 毫秒级时间戳 + 4位随机码
        String orderNo = "ORD" + System.currentTimeMillis() +
                String.format("%04d", (int) (Math.random() * 10000));

        // 组装最终流水
        Order order = Order.builder()
                .orderNo(orderNo)
                .user(reservation.getUser())
                .reservation(reservation)
                .parkingFee(parkingFee)
                .chargingFee(chargingFee)
                .serviceFee(serviceFee)
                .totalAmount(totalAmount)
                .paymentStatus(0) // 0=出金待付款
                .status(1)
                .build();

        return orderRepository.save(order);
    }

    /**
     * 模拟用户前端走完沙箱支付流水后，回调回来的切面
     */
    @Transactional
    public Order pay(Long orderId, String paymentMethod) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("订单已被重复标记为支付");
        }

        // --- 现实中这里会通过回调验证微信签名或者异步查单等操作，此处为模拟 ---

        order.setPaymentMethod(paymentMethod); // 如："WECHAT"
        order.setPaymentStatus(1); // 置为己付
        order.setPaymentTime(LocalDateTime.now());
        order.setStatus(2); // 订单业务流流转

        // 付款成功后，借助解耦好的消息服务去弹短信
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
            throw new RuntimeException("没付钱怎么可能强制拉取完单");
        }

        order.setStatus(3); // 3-彻底死档完成
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (order.getPaymentStatus() == 1) {
            throw new RuntimeException("这单钱都付了，不能单方面流标，请走售后退款");
        }

        order.setStatus(0); // 废弃撤销
        return orderRepository.save(order);
    }

    /**
     * 财务报表用：聚合指定时间段内完成交割完档的单量
     */
    public Long countCompletedOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countCompletedOrdersBetween(start, end);
    }

    /**
     * 财务报表用：聚合出在这个时间段内的总流水账额金钱总量
     */
    public BigDecimal calculateRevenue(LocalDateTime start, LocalDateTime end) {
        BigDecimal revenue = orderRepository.sumRevenueBetween(start, end);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    /**
     * 内部动态算法：计算阶梯定价的纯停车费用 (Time-of-Use pricing 核心逻辑)
     * 规则：
     * 早晚高峰拥堵期 (08:00-10:00, 17:00-21:00): 价格膨胀 1.5 倍
     * 深夜没人要谷期 (23:00-07:00): 打半价 0.5 倍
     * 其他时间: 原价 1.0 倍
     */
    private BigDecimal calculateDynamicFee(LocalDateTime start, LocalDateTime end, BigDecimal pricePerHour) {
        if (pricePerHour == null || start.isAfter(end)) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalFee = BigDecimal.ZERO;
        // 把单价按小时拍碎成到每一分钟头上的钱，以便最细颗粒度累计
        BigDecimal pricePerMinute = pricePerHour.divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);

        LocalDateTime current = start;
        // 暴力跑圈：每一分钟推过去，累加每一分钟那一会儿是什么价格段
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

        // 行业潜规则：进门拔毛最低消费防死耗线（默认只要接单了即便秒退，也收 1/4 小时费用起步）
        BigDecimal minimumFee = pricePerHour.divide(BigDecimal.valueOf(4), 2, java.math.RoundingMode.HALF_UP);
        if (totalFee.compareTo(minimumFee) < 0) {
            return minimumFee;
        }

        // 把最终值抹平回日常可读的保留2位小数点
        return totalFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
