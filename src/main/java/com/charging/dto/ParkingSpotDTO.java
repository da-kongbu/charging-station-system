package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 车位 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSpotDTO {
    private Long id;
    private String spotCode;
    private String spotNo;
    private String spotType;
    private BigDecimal pricePerHour;
    private BigDecimal serviceFee;
    private Integer status;
}
