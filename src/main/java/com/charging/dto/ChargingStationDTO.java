package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 充电站 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStationDTO {

    private Long id;
    private String name;
    private String address;
    private String city;
    private String district;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String contact;
    private String businessHours;
    private String description;
    private Integer status;
    private Integer pileCount;
    private Integer availablePileCount;
    private List<ChargingPileDTO> piles;
}
