package com.charging.service;

import com.charging.entity.ParkingSpot;
import com.charging.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 局部车位筛选服务
 * 
 * 作用：在预约大盘之外，提供一些针对单个场站或者零散车位的高频读操作查询封装。
 */
@Service
@RequiredArgsConstructor
public class ParkingSpotService {

    private final ParkingSpotRepository parkingSpotRepository;

    /**
     * 获取指定充电站底下当前状态全为空闲可用 (status=1) 的车位集合。
     * 常用于移动端点开某个站详情页时，底部横滑列表优先挑出空坑位展示。
     */
    public List<ParkingSpot> findAvailableByStationId(Long stationId) {
        return parkingSpotRepository.findAvailableByStationId(stationId);
    }

    /**
     * 智能时间窗探底查询：
     * 去数据库进行复杂的相交检测，返回在用户给定时间段内 [startTime, endTime]
     * 完完全全市没有被任何人征用或者预期的闲置车位列表。
     */
    public List<ParkingSpot> findAvailableSpotsForTime(LocalDateTime startTime, LocalDateTime endTime) {
        return parkingSpotRepository.findAvailableSpots(startTime, endTime);
    }
}
