package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 充电桩详细信息 DTO
 * 
 * 作用：用于向前端展示单个充电桩的具体硬件参数、状态以及其关联的车位信息。
 * 隐藏了底层数据库中可能存在但不该暴露给前端的其他敏感或无关字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPileDTO {

    /**
     * 充电桩在数据库中的唯一 ID
     */
    private Long id;

    /**
     * 充电桩的唯一标识编码 (通常印在设备上的二维码对应这个编码)
     */
    private String pileCode;

    /**
     * 充电桩在特定站点内的编号 (例如："A区01号桩")
     */
    private String pileNo;

    /**
     * 充电桩类型 (例如："AC" 交流慢充, "DC" 直流快充)
     */
    private String pileType;

    /**
     * 额定功率，单位：千瓦 (kW)，例如：7.0 或者 120.0
     */
    private BigDecimal power;

    /**
     * 额定电压，单位：伏特 (V)
     */
    private BigDecimal voltage;

    /**
     * 额定电流，单位：安培 (A)
     */
    private BigDecimal current;

    /**
     * 充电桩品牌制造商 (例如："特来电", "星星充电")
     */
    private String brand;

    /**
     * 连接器/枪头类型 (例如："Type2", "GB/T" 国标)
     */
    private String connectorType;

    /**
     * 充电桩当前的工作状态
     * (例如：0-离线，1-空闲可用，2-充电中)
     */
    private Integer status;

    /**
     * 挂载或者属于该充电桩的停车位列表。
     * 通常一个快充桩可能拖多个枪，对应多个车位。
     */
    private List<ParkingSpotDTO> parkingSpots;
}
