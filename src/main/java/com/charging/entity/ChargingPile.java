package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 充电桩实体类 (JPA Entity)
 * 
 * 作用：映射数据库中的 `charging_piles` 表。
 * 代表物理世界中一个可以插枪充电的具体设备（一根柱子）。
 */
@Entity // 标识这是一个受 JPA 管理的持久化映射类
@Table(name = "charging_piles") // 指定对应数据库中的数据表名
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingPile {

    /**
     * 主键 ID，自增策略
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的所属充电站实体
     * 关系类型：多对一 (多个桩属于同一个站)
     * FetchType.LAZY: 懒加载属性，只有在调用 getStation() 时才会查询数据库
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    @ToString.Exclude // 解决双向关联时 toString() 引发的无限循环引用报错
    @JsonIgnore // 防止将实体序列化回前端时引发无限嵌套的死循环
    private ChargingStation station;

    /**
     * 充电桩的唯一业务编码
     * unique = true 表示在数据库层面建立唯一索引保证不重复
     */
    @Column(name = "pile_code", unique = true, nullable = false, length = 50)
    private String pileCode;

    /**
     * 充电桩类型：DC-直流快充（充得快伤电池），AC-交流慢充（适合过夜充）
     */
    @Column(name = "pile_type", length = 20)
    private String pileType;

    /**
     * 最大输出功率（kW，千瓦）
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal power;

    /**
     * 额定输出电压（V，伏特），快充一般较高
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal voltage;

    /**
     * 额定输出电流（A，安培）
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal current;

    /**
     * 充电桩设备制造商的品牌名
     */
    @Column(length = 50)
    private String brand;

    /**
     * 支持的充电枪接头类型（例如国标 GB/T, 欧标 Type2，特斯拉等）
     */
    @Column(name = "connector_type", length = 50)
    private String connectorType;

    /**
     * 当前桩的空闲/工作状态：0-离线断网，1-网络在线且空闲可用，2-正在给汽车供电中，3-硬件故障报修中
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 一个充电桩可能同时带多个枪头，从而对应操作多个停车坑位
     * 关系类型：一对多
     * mappedBy = "pile": 表示关系的维护端在 ParkingSpot 类的 pile 属性上
     */
    @OneToMany(mappedBy = "pile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ParkingSpot> parkingSpots;

    /**
     * 该条记录的创建/录入时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 该条记录的最后一次修改时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA 回调方法：在执行 INSERT 入库操作前，自动执行回调把创建/更新时间填上当前系统时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA 回调方法：在执行 UPDATE 语句前，自动用最新时间覆盖修改时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
