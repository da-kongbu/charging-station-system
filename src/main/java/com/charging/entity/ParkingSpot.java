package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车位实体类 (JPA Entity)
 * 
 * 作用：映射数据库的 `parking_spots` 表。
 * 代表物理世界中用来泊车的一个真实长方形格子。
 */
@Entity
@Table(name = "parking_spots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 该车位旁边立着的是哪一个充电桩（即归属于哪个桩）
     * FetchType.LAZY 懒加载，提高查询列表时的性能
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pile_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private ChargingPile pile;

    /**
     * 系统内部识别用的车位维一编码卡号
     */
    @Column(name = "spot_code", unique = true, nullable = false, length = 50)
    private String spotCode;

    /**
     * 车位的尺寸类型：
     * STANDARD-标准轿车位，LARGE-大型车宽体车位，SMALL-老头乐/微型车位
     */
    @Column(name = "spot_type", length = 20)
    @Builder.Default
    private String spotType = "STANDARD";

    /**
     * 该坑位的纯停车占位费费率（元/小时）
     * （比如防止充满电不走的人，收取高昂的超时占位费）
     */
    @Column(name = "price_per_hour", precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    /**
     * 充电服务费（元/度）
     */
    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    /**
     * 坑位实时地锁状态监控：
     * 0-因为故障或积水不可用，1-空闲中（地锁升起），2-已被人按下了预约（地锁保留），3-汽车已经停入（地锁降下/超声波探头触发）
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
}
