package com.charging.service;

import com.charging.entity.ParkingSpot;
import com.charging.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车位服务
 */
@Service
@RequiredArgsConstructor
public class ParkingSpotService {

    private final ParkingSpotRepository parkingSpotRepository;

    /**
     * 获取充电站的可用车位
     */
    public List<ParkingSpot> findAvailableByStationId(Long stationId) {
        return parkingSpotRepository.findAvailableByStationId(stationId);
    }

    /**
     * 查询指定时间段的可用车位
     */
    public List<ParkingSpot> findAvailableSpotsForTime(LocalDateTime startTime, LocalDateTime endTime) {
        return parkingSpotRepository.findAvailableSpots(startTime, endTime);
    }
}
