package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 充电桩 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPileDTO {
    private Long id;
    private String pileCode;
    private String pileNo;
    private String pileType;
    private BigDecimal power;
    private BigDecimal voltage;
    private BigDecimal current;
    private String brand;
    private String connectorType;
    private Integer status;
    private List<ParkingSpotDTO> parkingSpots;
}
