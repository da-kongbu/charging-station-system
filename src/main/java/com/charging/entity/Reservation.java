package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 资源预约记录实体类 (JPA Entity)
 * 
 * 作用：映射数据库的 `reservations` 表。
 * 代表着某人要在某个时间段借用某个车位桩的契约协议。
 */
@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 这是哪一个用户发起的霸位预约
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /**
     * 这个预约抢占的是哪一个具体的停车位资源
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    @ToString.Exclude
    private ParkingSpot spot;

    /**
     * 用户承诺的用车起效开始时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 用户预计拔枪走人的结束时间（决定了能向他收多少押金）
     */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /**
     * 用户真实开车道闸抬杠进入场地的打卡时间
     */
    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    /**
     * 用户真实开车出去的系统结算时间
     */
    @Column(name = "actual_leave_time")
    private LocalDateTime actualLeaveTime;

    /**
     * 当时承诺要开进来的车牌号（防占位，只有这个牌照的车才能降下预约地锁）
     */
    @Column(name = "car_plate", length = 20)
    private String carPlate;

    /**
     * 整个生命周期状态机：
     * 0-自行或被系统已取消
     * 1-锁定了名额待使用
     * 2-插枪通电使用中
     * 3-充电结束已离开
     * 4-预定的时间到了还没来，信誉扣分并已过期
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 用户的文本附加留言
     */
    @Column(length = 500)
    private String remark;

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
