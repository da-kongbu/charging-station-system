package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.dto.ChargingStationDTO;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingStation;
import com.charging.entity.ParkingSpot;
import com.charging.service.ChargingStationService;
import com.charging.service.ParkingSpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 充电站控制器
 */
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Tag(name = "充电站管理", description = "充电站信息查询接口")
public class ChargingStationController {

    private final ChargingStationService stationService;
    private final ParkingSpotService parkingSpotService;

    @GetMapping
    @Operation(summary = "获取所有充电站列表")
    public ResponseEntity<ApiResponse<List<ChargingStationDTO>>> getAllStations() {
        List<ChargingStationDTO> stations = stationService.findAllAvailable();
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取充电站详情")
    public ResponseEntity<ApiResponse<ChargingStationDTO>> getStation(@PathVariable(name = "id") Long id) {
        ChargingStationDTO station = stationService.findById(id)
                .orElseThrow(() -> new RuntimeException("充电站不存在"));
        return ResponseEntity.ok(ApiResponse.success(station));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索充电站")
    public ResponseEntity<ApiResponse<List<ChargingStationDTO>>> searchStations(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "city", required = false) String city) {
        List<ChargingStationDTO> stations;
        if (keyword != null && !keyword.isEmpty()) {
            stations = stationService.searchByKeyword(keyword);
        } else if (city != null && !city.isEmpty()) {
            stations = stationService.findByCity(city);
        } else {
            stations = stationService.findAllAvailable();
        }
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    @GetMapping("/{id}/piles")
    @Operation(summary = "获取充电站的充电桩列表")
    public ResponseEntity<ApiResponse<List<ChargingPile>>> getStationPiles(@PathVariable(name = "id") Long id) {
        List<ChargingPile> piles = stationService.getPilesByStationId(id);
        return ResponseEntity.ok(ApiResponse.success(piles));
    }

    @GetMapping("/{id}/spots")
    @Operation(summary = "获取充电站的可用车位")
    public ResponseEntity<ApiResponse<List<ParkingSpot>>> getAvailableSpots(@PathVariable(name = "id") Long id) {
        List<ParkingSpot> spots = parkingSpotService.findAvailableByStationId(id);
        return ResponseEntity.ok(ApiResponse.success(spots));
    }

    @GetMapping("/spots/available")
    @Operation(summary = "查询指定时间段的可用车位")
    public ResponseEntity<ApiResponse<List<ParkingSpot>>> getAvailableSpotsForTime(
            @RequestParam(name = "startTime") LocalDateTime startTime,
            @RequestParam(name = "endTime") LocalDateTime endTime) {
        List<ParkingSpot> spots = parkingSpotService.findAvailableSpotsForTime(startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(spots));
    }
}
