package com.charging.service;

import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未签到预约自动失效服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryService {

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cancelOverdueReservations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime graceCutoff = now.minusMinutes(30);

        List<Reservation> overdueReservations = reservationRepository.findExpiredReservations(now, graceCutoff);
        if (overdueReservations.isEmpty()) {
            return;
        }

        for (Reservation reservation : overdueReservations) {
            reservation.setStatus(0);

            ParkingSpot spot = reservation.getSpot();
            if (spot != null && spot.getStatus() != 0) {
                spot.setStatus(1);
                parkingSpotRepository.save(spot);
            }
        }

        log.info("自动取消了 {} 个超时未签到的预约", overdueReservations.size());
    }
}
