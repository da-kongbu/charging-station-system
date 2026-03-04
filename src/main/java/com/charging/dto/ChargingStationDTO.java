package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 充电站 DTO (Data Transfer Object)
 * 
 * 作用：用于封装一个充电站（即一个物理存在的场地，包含多个充电桩）的详细信息，
 * 并发送给前端。常用于地图选点、场站列表展示页。
 */
@Data // 自动生成获取、设置、等值比较的方法
@Builder // 支持链式构建对象
@NoArgsConstructor // 自动生成无参构造器
@AllArgsConstructor // 自动生成全参构造器
public class ChargingStationDTO {

    /**
     * 充电站记录的唯一 ID (主键)
     */
    private Long id;

    /**
     * 充电站的用户可见名称 (例如："清华科技园地下超级充电站")
     */
    private String name;

    /**
     * 充电站的详细文字地址
     */
    private String address;

    /**
     * 所属地级市 (例如："北京市")
     */
    private String city;

    /**
     * 所属行政区划 (例如："海淀区")
     */
    private String district;

    /**
     * 地理位置坐标 - 经度 (通过第三方地图API打点所需)
     */
    private BigDecimal longitude;

    /**
     * 地理位置坐标 - 纬度 (通过第三方地图API打点所需)
     */
    private BigDecimal latitude;

    /**
     * 场站联系人电话或服务热线
     */
    private String contact;

    /**
     * 场站营业时间范围描述 (例如："00:00 - 23:59" 或 "24小时营业")
     */
    private String businessHours;

    /**
     * 场站的图文描述，如图文介绍、停车收费标准等说明
     */
    private String description;

    /**
     * 场站当前的运营状态
     * 0: 停业/维护中
     * 1: 正常营业
     */
    private Integer status;

    /**
     * 该站点内拥有的充电桩总数量
     */
    private Integer pileCount;

    /**
     * 该站点内当前空闲可用的充电桩数量，用于前端实时呈现给用户，指导前往
     */
    private Integer availablePileCount;

    /**
     * 该站内挂载的所有具体的充电桩设备列表，包含各个设备的详细参数
     */
    private List<ChargingPileDTO> piles;
}
