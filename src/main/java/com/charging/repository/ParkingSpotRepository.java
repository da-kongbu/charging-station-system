package com.charging.repository;

import com.charging.entity.ParkingSpot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

       /**
        * 使用悲观锁查询车位，防止并发预约同一车位
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT ps FROM ParkingSpot ps WHERE ps.id = :id")
       Optional<ParkingSpot> findByIdForUpdate(@Param("id") Long id);

       List<ParkingSpot> findByPileId(Long pileId);

       List<ParkingSpot> findByPileIdAndStatus(Long pileId, Integer status);

       Optional<ParkingSpot> findBySpotCode(String spotCode);

       boolean existsBySpotCode(String spotCode);

       @Query("SELECT ps FROM ParkingSpot ps WHERE ps.pile.station.id = :stationId AND ps.status = 1")
       List<ParkingSpot> findAvailableByStationId(@Param("stationId") Long stationId);

       @Query("SELECT ps FROM ParkingSpot ps WHERE ps.status <> 0 AND ps.id NOT IN " +
                     "(SELECT r.spot.id FROM Reservation r WHERE r.status IN (1, 2) " +
                     "AND ((r.startTime <= :endTime AND r.endTime >= :startTime)))")
       List<ParkingSpot> findAvailableSpots(@Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);
}
