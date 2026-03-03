package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约详情 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDTO {

    private Long id;
    private Long userId;
    private String username;
    private Long spotId;
    private String spotCode;
    private Long pileId;
    private String pileCode;
    private String pileType;
    private BigDecimal pilePower;
    private Long stationId;
    private String stationName;
    private String stationAddress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String carPlate;
    private Integer status;
    private String statusText;
    private BigDecimal estimatedCost;
    private LocalDateTime createdAt;

    public static String getStatusText(Integer status) {
        return switch (status) {
            case 0 -> "已取消";
            case 1 -> "待使用";
            case 2 -> "使用中";
            case 3 -> "已完成";
            case 4 -> "已过期";
            default -> "未知";
        };
    }
}
