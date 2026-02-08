package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 预约实体类
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    @ToString.Exclude
    private ParkingSpot spot;

    /**
     * 预约开始时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 预约结束时间
     */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /**
     * 实际到达时间
     */
    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    /**
     * 实际离开时间
     */
    @Column(name = "actual_leave_time")
    private LocalDateTime actualLeaveTime;

    /**
     * 车牌号
     */
    @Column(name = "car_plate", length = 20)
    private String carPlate;

    /**
     * 预约状态：0-已取消，1-待使用，2-使用中，3-已完成，4-已过期
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 备注
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
