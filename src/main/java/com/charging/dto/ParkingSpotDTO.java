package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 停车位 DTO (Data Transfer Object)
 * 
 * 作用：用于封装单个能够停车和充电的位置的状态、计费标准等信息返回给前端。
 * 是预约业务逻辑的核心实体之一。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSpotDTO {

    /**
     * 车位的唯一数据库主键 ID
     */
    private Long id;

    /**
     * 内部标识该车位的系统唯一编码
     */
    private String spotCode;

    /**
     * 该车位面向用户的显示编号 (例如："B1-032" 或是 "A区01")
     */
    private String spotNo;

    /**
     * 车位的分类 (例如："VIP", "普通", "残障人士专用")
     */
    private String spotType;

    /**
     * 该车位停靠所需的占位费率 (单位：元/小时)
     * 有些场站除了收电费，按停车时长也要单独计费
     */
    private BigDecimal pricePerHour;

    /**
     * 每次使用本车位/对应充电桩固定收取的服务基础费用
     */
    private BigDecimal serviceFee;

    /**
     * 车位的实时占用状态
     * 常用的状态位可能是：0-空闲, 1-已被预约(但车未到), 2-已被占用(车已停), 3-维护中不可用
     */
    private Integer status;
}
