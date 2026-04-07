package com.charging.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 充电站场实体类 (JPA Entity)
 * 
 * 作用：映射数据库的 `charging_stations` 表。
 * 代表物理世界中的一个场站（类似于一个加油站），内部会拥有多根充电桩 (ChargingPile)。
 */
@Entity
@Table(name = "charging_stations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingStation {

    /**
     * 充电站的主键 ID，数据库自动递增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 场站对外运营显示的名称，不允许为空，最长100字符
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 场站所在的详细地理街道地址，用于导航显示
     */
    @Column(nullable = false, length = 255)
    private String address;

    /**
     * 所在城市（例如："上海市"）
     */
    @Column(length = 100)
    private String city;

    /**
     * 所在的区县（例如："浦东新区"）
     */
    @Column(length = 100)
    private String district;

    /**
     * 地理经度坐标 (用于接入百度/高德地图 SDK 计算距离时必传)
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * 地理纬度坐标
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * 场站的场站长联系电话或官方服务热线客服电话
     */
    @Column(length = 50)
    private String contact;

    /**
     * 场站对外开放的营业时间范围 (例如："08:00 - 22:00")
     */
    @Column(name = "business_hours", length = 100)
    private String businessHours;

    /**
     * 场站额外描述说明 (比如周边吃饭点、进场免费时间、卫生间情况等长文本)
     * 指定列定义为 TEXT 类型容纳较长的数据
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 场站级别整体当前的运营营业状态：
     * 0-暂停营业，1-正常营业，2-维护中
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 维护双向的一对多关系 (一个站里面有一堆充电柱子桩)
     * cascade = CascadeType.ALL 表示如果删除了一个站点，级联一并把它下属的所有的桩都顺路删掉
     */
    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ChargingPile> piles;

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
