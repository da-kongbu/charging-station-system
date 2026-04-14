package com.charging.repository;

import com.charging.entity.ChargingStation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ChargingStationRepositoryTest {

    @Autowired
    private ChargingStationRepository chargingStationRepository;

    @Test
    void searchByKeywordShouldMatchCityField() {
        ChargingStation station = ChargingStation.builder()
                .name("滨江快充站")
                .address("科技园路 88 号")
                .city("上海")
                .status(1)
                .build();
        chargingStationRepository.save(station);

        assertThat(chargingStationRepository.searchByKeyword("上海"))
                .extracting(ChargingStation::getName)
                .contains("滨江快充站");
    }
}
