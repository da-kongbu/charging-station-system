package com.charging.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReservationCardData {
    private Long reservationId;
    private String stationName;
    private String spotCode;
    private String startTime;
    private String endTime;
    private String status;
}
