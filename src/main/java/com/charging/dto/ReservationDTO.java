package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约详情展示 DTO (Data Transfer Object)
 * 
 * 作用：这是整个系统最核心、最复杂的视图传输模型。它聚合了用户、桩、车位、站等多个数据库表的信息，
 * 封装在一个整票中返还给前端展示，常被用于 "我的订单/我的行程" 页面。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDTO {

    /**
     * 这笔预约业务的全局唯一流水 ID
     */
    private Long id;

    /**
     * 该笔预约对应的下单人信息
     * (userId 用于逻辑查验，username 用于前台展示类似 "Hi, admin")
     */
    private Long userId;
    private String username;

    /**
     * 成功锁定到的实体车位资源
     * (spotId 是查询主键，spotCode 给用户找对应柱子提供依据)
     */
    private Long spotId;
    private String spotCode;

    /**
     * 用于即将进行的充电操作的桩体信息
     */
    private Long pileId;
    private String pileCode;
    private String pileType; // 告诉用户是快充还慢充
    private BigDecimal pilePower; // 显示最大支持功率

    /**
     * 该停车位和充电桩从属于哪一个集镇站，并包含站的基础信息
     */
    private Long stationId;
    private String stationName;
    private String stationAddress; // 用于前端拉起导航组件

    /**
     * 用户所预订的车位霸占时段起止点 (即这辆车能在什么时间段使用该资源)
     */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * 用户当时下单关联开过来的车牌号（方便道闸放行核对）
     */
    private String carPlate;

    /**
     * 订单进度/状态枚举值和对应的多语种(中文)显示文本
     * (如 3 对应 "已完成")
     */
    private Integer status;
    private String statusText;

    /**
     * 基于停车费+预估充电量算出来的锁定冻结金额
     */
    private BigDecimal estimatedCost;

    /**
     * 用户点击 "立刻预约" 按下刹那的创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 辅助工具方法：用来根据内部数字状态码(status)翻译成可供用户查阅的对应文本。
     * 使用了 JDK 14+ 引入的增强版 Switch 表达式语法。
     *
     * @param status 在数据库里存储的状态数字 (如 2)
     * @return 给用户看的提示文字 (如 "使用中")
     */
    public static String getStatusText(Integer status) {
        return switch (status) { // 增强型 switch 语法特性
            case 0 -> "已取消";
            case 1 -> "待使用"; // 已经付了押金抢到了，还没开过去插枪
            case 2 -> "使用中"; // 正在充
            case 3 -> "已完成"; // 拔枪结账走人
            case 4 -> "已过期"; // 约了但是放鸽子了
            default -> "未知";
        };
    }
}
