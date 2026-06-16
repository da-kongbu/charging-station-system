package com.charging.service;

import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationExpiryServiceTest {

    @Test
    void cancelOverdueReservationsShouldCancelReservationAndReleaseSpot() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ParkingSpotRepository parkingSpotRepository = mock(ParkingSpotRepository.class);

        ReservationExpiryService service = new ReservationExpiryService(reservationRepository, parkingSpotRepository);

        ParkingSpot spot = ParkingSpot.builder()
                .id(7L)
                .status(2)
                .build();
        Reservation reservation = Reservation.builder()
                .id(13L)
                .status(1)
                .startTime(LocalDateTime.now().minusMinutes(31))
                .endTime(LocalDateTime.now().plusHours(1))
                .spot(spot)
                .build();

        when(reservationRepository.findExpiredReservations(any(), any())).thenReturn(List.of(reservation));

        service.cancelOverdueReservations();

        assertThat(reservation.getStatus()).isEqualTo(0);
        assertThat(spot.getStatus()).isEqualTo(1);
        verify(parkingSpotRepository).save(spot);
    }
}
