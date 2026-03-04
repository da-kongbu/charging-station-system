package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 实时充电状态 DTO (Data Transfer Object)
 * 
 * 作用：当用户成功插枪开始充电后，前端通过定时轮询或 WebSocket 接收这个对象，
 * 用于在屏幕上实时绘制正在充电的进度条、仪表盘等信息（例如实时电压、百分比）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusDTO {

    /**
     * 关联的预约订单 ID，表明这是哪一次预约产生的充电行为
     */
    private Long reservationId;

    /**
     * 当前充电生命周期的状态字
     * "CHARGING": 正在进行中
     * "COMPLETED": 已满电或用户主动终止
     * "ERROR": 设备故障或连接异常
     */
    private String status;

    /**
     * 实时检测到的充电电压，单位：伏特(V)
     */
    private BigDecimal voltage;

    /**
     * 实时检测到的输入电流，单位：安培(A)
     */
    private BigDecimal current;

    /**
     * 实时输出总功率，单位：千瓦(kW)
     */
    private BigDecimal power;

    /**
     * 电池荷电状态 (State Of Charge, SOC)，即当前电量百分比，范围 0-100。
     * 是前端渲染充电进度条的最核心字段。
     */
    private Integer soc;

    /**
     * 预估充满所需剩余时间，单位：分钟(minutes)
     */
    private Integer remainingTime;

    /**
     * 截至目前该次充电累计已充入的电能度数，单位：千瓦时(kWh/度)。
     * 后续将根据此数值计算用户的最终电费。
     */
    private BigDecimal chargedEnergy;
}
