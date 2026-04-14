package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryAvailableSpotsArgs {
    private Long stationId;
    private String startTime;
    private String endTime;
    private String chargingType;
}
