package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 推荐预约卡片数据 — 只读，不创建预约
 * 前端收到后跳转到 StationDetail 页面确认
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendBookingCardData {
    private Long stationId;
    private String stationName;
    private Long spotId;
    private String spotCode;
    private String chargingType;
    private String startTime;
    private String endTime;
    private java.math.BigDecimal pricePerHour;
    private String reason;
}
