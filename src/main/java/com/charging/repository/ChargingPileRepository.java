package com.charging.repository;

import com.charging.entity.ChargingPile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingPileRepository extends JpaRepository<ChargingPile, Long> {
    
    List<ChargingPile> findByStationId(Long stationId);
    
    List<ChargingPile> findByStationIdAndStatus(Long stationId, Integer status);
    
    Optional<ChargingPile> findByPileCode(String pileCode);
    
    List<ChargingPile> findByPileType(String pileType);
    
    boolean existsByPileCode(String pileCode);
}
