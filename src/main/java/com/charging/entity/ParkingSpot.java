package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车位实体类
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pile_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private ChargingPile pile;

    /**
     * 车位编码
     */
    @Column(name = "spot_code", unique = true, nullable = false, length = 50)
    private String spotCode;

    /**
     * 车位类型：STANDARD-标准车位，LARGE-大型车位，SMALL-小型车位
     */
    @Column(name = "spot_type", length = 20)
    @Builder.Default
    private String spotType = "STANDARD";

    /**
     * 每小时价格（元）
     */
    @Column(name = "price_per_hour", precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    /**
     * 充电服务费（元/度）
     */
    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    /**
     * 状态：0-不可用，1-空闲，2-已预约，3-使用中
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
