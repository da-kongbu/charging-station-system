package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationCardData {
    private Long id;
    private String name;
    private String address;
    private String city;
    private Integer pileCount;
    private Integer availablePileCount;
    private String businessHours;
    private Double distanceKm;
}
