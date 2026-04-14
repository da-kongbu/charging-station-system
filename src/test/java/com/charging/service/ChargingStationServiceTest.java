package com.charging.service;

import com.charging.dto.ChargingStationDTO;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.repository.ChargingPileRepository;
import com.charging.repository.ChargingStationRepository;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import com.charging.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChargingStationServiceTest {

    @Test
    void findAllAvailableShouldExcludeStationsWithoutAvailablePiles() {
        ChargingStationRepository stationRepository = mock(ChargingStationRepository.class);
        ChargingPileRepository pileRepository = mock(ChargingPileRepository.class);
        ParkingSpotRepository spotRepository = mock(ParkingSpotRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        ChargingStationService service = new ChargingStationService(
                stationRepository, pileRepository, spotRepository, reservationRepository, userRepository);

        ChargingStation fullStation = ChargingStation.builder()
                .id(1L)
                .name("满桩站点")
                .address("A 路 1 号")
                .status(1)
                .build();
        ChargingStation availableStation = ChargingStation.builder()
                .id(2L)
                .name("空闲站点")
                .address("B 路 2 号")
                .status(1)
                .build();

        ChargingPile fullPile = ChargingPile.builder().id(11L).station(fullStation).pileCode("PILE-FULL").pileType("DC").status(1).build();
        ChargingPile availablePile = ChargingPile.builder().id(12L).station(availableStation).pileCode("PILE-OK").pileType("DC").status(1).build();

        ParkingSpot reservedSpot = ParkingSpot.builder()
                .id(101L).pile(fullPile).spotCode("SPOT-FULL").spotType("STANDARD").status(2)
                .pricePerHour(BigDecimal.TEN).build();
        ParkingSpot availableSpot = ParkingSpot.builder()
                .id(102L).pile(availablePile).spotCode("SPOT-OK").spotType("STANDARD").status(1)
                .pricePerHour(BigDecimal.TEN).build();

        when(stationRepository.findAllAvailable()).thenReturn(List.of(fullStation, availableStation));
        when(pileRepository.findByStationId(1L)).thenReturn(List.of(fullPile));
        when(pileRepository.findByStationId(2L)).thenReturn(List.of(availablePile));
        when(spotRepository.findByPileId(11L)).thenReturn(List.of(reservedSpot));
        when(spotRepository.findByPileId(12L)).thenReturn(List.of(availableSpot));
        when(reservationRepository.findConflictingReservations(eq(102L), any(), any()))
                .thenReturn(List.of());

        List<ChargingStationDTO> result = service.findAllAvailable();

        assertThat(result).extracting(ChargingStationDTO::getName)
                .containsExactly("空闲站点");
    }
}
