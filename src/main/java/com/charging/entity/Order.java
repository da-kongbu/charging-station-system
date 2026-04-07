package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单/订单实体类 (JPA Entity)
 * 
 * 作用：映射数据库的 `orders` 表。
 * 代表用户消费完之后生成的最终账单集合，包含各种明细费用和支付状态信息。
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" }) // 忽略 JPA 懒加载代理产生的序列化不必要的内部字段
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 面向用户的全局唯一的订单流水号（例如："ORD20261122153300001"）
     * 用于向微信/支付宝发起支付请求的商户订单号
     */
    @Column(name = "order_no", unique = true, nullable = false, length = 50)
    private String orderNo;

    /**
     * 该订单归属于哪个用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private User user;

    /**
     * 这张总账单是对哪次具体的行程预约操作结算的
     * 关系为一对一 (@OneToOne)
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Reservation reservation;

    /**
     * 产生的停车坑位费用明细（如果在非免费时间段内停车）
     */
    @Column(name = "parking_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal parkingFee = BigDecimal.ZERO;

    /**
     * 用电成本费用（纯粹的电量的钱，基于国家规定电价等按时间段计算所得）
     */
    @Column(name = "charging_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal chargingFee = BigDecimal.ZERO;

    /**
     * 充电服务附加费 (由桩企或者场站方加收的服务维护溢价费)
     */
    @Column(name = "service_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    /**
     * 用户最终需要实际支付结算的总金额款项 (等于上面三个费用累加)
     */
    @Column(name = "total_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * 行程结算得出的实际消耗电表的充电总度数总量（kWh千瓦时/度）
     */
    @Column(name = "charging_amount", precision = 10, scale = 2)
    private BigDecimal chargingAmount;

    /**
     * 车辆实际插枪占用充电的净时长数（分钟计）
     */
    @Column(name = "charging_duration")
    private Integer chargingDuration;

    /**
     * 选择的结算支付方式渠道的标记词汇：
     * WECHAT-微信支付，ALIPAY-支付宝，BALANCE-应用内充值余额支付
     */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /**
     * 订单实时的支付结果流转状态：
     * 0-生成账单待支付，1-用户已经付款成功，2-向三方渠道申请退款处于退款中，3-这笔钱已经退回账户
     */
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private Integer paymentStatus = 0;

    /**
     * 支付宝或微信回调通知到账的确切支付时间记录点
     */
    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    /**
     * 订单业务进展大状态：
     * 0-超时未支付被取消，1-排队产生待支付中，2-付款已结清确认完成，3-彻底完结归档
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
     * 辅助防空指针方法：便利获取与本单关联的用户登录名
     */
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }
}
