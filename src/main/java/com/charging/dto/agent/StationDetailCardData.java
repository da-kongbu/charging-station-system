package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationDetailCardData {
    private Long id;
    private String name;
    private String address;
    private String businessHours;
    private String contact;
    private Integer pileCount;
    private Integer availablePileCount;
    private List<Map<String, Object>> piles;
}
