package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 充电桩实体类
 */
@Entity
@Table(name = "charging_piles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingPile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private ChargingStation station;

    /**
     * 充电桩编码
     */
    @Column(name = "pile_code", unique = true, nullable = false, length = 50)
    private String pileCode;

    /**
     * 充电桩类型：DC-直流快充，AC-交流慢充
     */
    @Column(name = "pile_type", length = 20)
    private String pileType;

    /**
     * 功率（kW）
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal power;

    /**
     * 电压（V）
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal voltage;

    /**
     * 电流（A）
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal current;

    /**
     * 充电桩品牌
     */
    @Column(length = 50)
    private String brand;

    /**
     * 支持的接口类型
     */
    @Column(name = "connector_type", length = 50)
    private String connectorType;

    /**
     * 状态：0-离线，1-空闲，2-使用中，3-故障
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @OneToMany(mappedBy = "pile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ParkingSpot> parkingSpots;

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
