package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 实时充电状态 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusDTO {
    private Long reservationId;
    private String status; // CHARGING, COMPLETED, ERROR
    private BigDecimal voltage; // V
    private BigDecimal current; // A
    private BigDecimal power; // kW
    private Integer soc; // % (State Of Charge)
    private Integer remainingTime; // minutes
    private BigDecimal chargedEnergy; // kWh
}
