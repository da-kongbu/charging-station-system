package com.charging.repository;

import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ParkingSpotRepositoryTest {

    @Autowired
    private ChargingStationRepository chargingStationRepository;

    @Autowired
    private ChargingPileRepository chargingPileRepository;

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    @Test
    void findAvailableSpotsShouldIncludeReservedStatusSpotWhenTimeWindowDoesNotConflict() {
        ChargingStation station = chargingStationRepository.save(ChargingStation.builder()
                .name("未来充电站")
                .address("测试路 1 号")
                .city("上海")
                .status(1)
                .build());

        ChargingPile pile = chargingPileRepository.save(ChargingPile.builder()
                .station(station)
                .pileCode("PILE-T-001")
                .pileType("DC")
                .power(BigDecimal.valueOf(120))
                .status(1)
                .build());

        ParkingSpot reservedSpot = parkingSpotRepository.save(ParkingSpot.builder()
                .pile(pile)
                .spotCode("SPOT-T-001")
                .spotType("STANDARD")
                .status(2)
                .pricePerHour(BigDecimal.valueOf(8))
                .serviceFee(BigDecimal.ONE)
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        assertThat(parkingSpotRepository.findAvailableSpots(start, end))
                .extracting(ParkingSpot::getId)
                .contains(reservedSpot.getId());
    }
}
