package com.charging.service;

import com.charging.dto.ReservationDTO;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationServiceTest {

    @Test
    void convertToDtoShouldExposeActualArrivalAndLeaveTimes() {
        ReservationService reservationService = new ReservationService(null, null, null, null);

        LocalDateTime startTime = LocalDateTime.of(2026, 4, 13, 10, 0);
        LocalDateTime endTime = startTime.plusHours(2);
        LocalDateTime actualArrivalTime = startTime.plusMinutes(3);
        LocalDateTime actualLeaveTime = startTime.plusHours(1).plusMinutes(20);

        ChargingStation station = ChargingStation.builder()
                .id(3L)
                .name("秋枫充电站")
                .address("常州市武进区")
                .build();

        ChargingPile pile = ChargingPile.builder()
                .id(5L)
                .pileCode("PILE-001")
                .pileType("DC")
                .power(BigDecimal.valueOf(120))
                .station(station)
                .build();

        ParkingSpot spot = ParkingSpot.builder()
                .id(7L)
                .spotCode("SPOT-001")
                .pricePerHour(BigDecimal.valueOf(6))
                .pile(pile)
                .build();

        User user = User.builder()
                .id(11L)
                .username("tester")
                .build();

        Reservation reservation = Reservation.builder()
                .id(13L)
                .user(user)
                .spot(spot)
                .startTime(startTime)
                .endTime(endTime)
                .actualArrivalTime(actualArrivalTime)
                .actualLeaveTime(actualLeaveTime)
                .status(2)
                .createdAt(startTime.minusHours(1))
                .build();

        ReservationDTO dto = ReflectionTestUtils.invokeMethod(reservationService, "convertToDTO", reservation);

        assertThat(dto).isNotNull();
        assertThat(dto.getActualArrivalTime()).isEqualTo(actualArrivalTime);
        assertThat(dto.getActualLeaveTime()).isEqualTo(actualLeaveTime);
        assertThat(dto.getPilePower()).isEqualByComparingTo("120");
    }
}
