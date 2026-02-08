package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 订单编号
     */
    @Column(name = "order_no", unique = true, nullable = false, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    private Reservation reservation;

    /**
     * 停车费用（元）
     */
    @Column(name = "parking_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal parkingFee = BigDecimal.ZERO;

    /**
     * 充电费用（元）
     */
    @Column(name = "charging_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal chargingFee = BigDecimal.ZERO;

    /**
     * 服务费（元）
     */
    @Column(name = "service_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    /**
     * 总金额（元）
     */
    @Column(name = "total_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * 充电度数（kWh）
     */
    @Column(name = "charging_amount", precision = 10, scale = 2)
    private BigDecimal chargingAmount;

    /**
     * 充电时长（分钟）
     */
    @Column(name = "charging_duration")
    private Integer chargingDuration;

    /**
     * 支付方式：WECHAT-微信，ALIPAY-支付宝，BALANCE-余额
     */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /**
     * 支付状态：0-未支付，1-已支付，2-退款中，3-已退款
     */
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private Integer paymentStatus = 0;

    /**
     * 支付时间
     */
    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    /**
     * 订单状态：0-已取消，1-待支付，2-已支付，3-已完成
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 获取用户名（用于JSON序列化）
     */
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }
}
