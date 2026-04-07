package com.charging.service;

import com.charging.entity.ParkingSpot;
import com.charging.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车位查询服务
 *
 * 作用：提供车位可用性查询能力。
 */
@Service
@RequiredArgsConstructor
public class ParkingSpotService {
    private final ParkingSpotRepository parkingSpotRepository;

    /**
     * 获取指定充电站当前可用的车位列表
     */
    public List<ParkingSpot> findAvailableByStationId(Long stationId) {
        return parkingSpotRepository.findAvailableByStationId(stationId);
    }

    /**
     * 获取指定时间段内可预约的车位列表
     */
    public List<ParkingSpot> findAvailableSpotsForTime(LocalDateTime startTime, LocalDateTime endTime) {
        return parkingSpotRepository.findAvailableSpots(startTime, endTime);
    }
}
