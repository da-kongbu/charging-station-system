package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSpotCardData {
    private Long spotId;
    private String spotCode;
    private String spotType;
    private String chargingType;
    private BigDecimal pricePerHour;
    private BigDecimal serviceFee;
}
