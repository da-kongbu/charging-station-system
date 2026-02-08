package com.charging.repository;

import com.charging.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(Long userId);

    List<Reservation> findByUserIdAndStatus(Long userId, Integer status);

    List<Reservation> findBySpotId(Long spotId);

    List<Reservation> findBySpotIdAndStatus(Long spotId, Integer status);

    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<Reservation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT r FROM Reservation r WHERE r.spot.id = :spotId AND r.status IN (1, 2) " +
            "AND ((r.startTime <= :endTime AND r.endTime >= :startTime))")
    List<Reservation> findConflictingReservations(@Param("spotId") Long spotId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT r FROM Reservation r WHERE r.spot.id = :spotId AND r.status IN (1, 2) " +
            "AND r.startTime >= :startOfDay AND r.startTime < :endOfDay")
    List<Reservation> findBySpotIdAndDateRange(@Param("spotId") Long spotId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT r FROM Reservation r WHERE r.status = 1 AND r.startTime < :now")
    List<Reservation> findExpiredReservations(@Param("now") LocalDateTime now);
}
