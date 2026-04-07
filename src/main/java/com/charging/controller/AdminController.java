package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.entity.*;
import com.charging.repository.*;
import com.charging.service.ChargingStationService;
import com.charging.service.OrderService;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台接口
 *
 * 作用：提供用户、充电站、充电桩、车位和订单等后台管理能力。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理后台", description = "管理员专用接口")
public class AdminController {

    private final UserService userService;
    private final ChargingStationService stationService;
    private final OrderService orderService;
    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;

    // 仪表盘统计

    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("totalUsers", userService.findAll().size());
        dashboard.put("totalStations", stationRepository.count());
        dashboard.put("totalPiles", pileRepository.count());
        dashboard.put("totalSpots", spotRepository.count());

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now();

        Long todayOrders = orderService.countCompletedOrders(todayStart, todayEnd);
        BigDecimal todayRevenue = orderService.calculateRevenue(todayStart, todayEnd);

        long todayReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(todayStart))
                .count();

        dashboard.put("todayOrders", todayOrders);
        dashboard.put("todayReservations", todayReservations);
        dashboard.put("todayRevenue", todayRevenue);

        List<Order> recentOrders = orderService.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .toList();
        dashboard.put("recentOrders", recentOrders);

        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    // 用户管理

    @GetMapping("/users")
    @Operation(summary = "获取所有用户列表")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAll();
        // 实际生产环境建议增加分页能力
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "更新用户状态，如拉黑/解封")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "status") Integer status) {
        userService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("用户状态已更新", null));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "注销/清退删除用户")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable(name = "id") Long id) {
        userService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }

    // 充电站管理

    @GetMapping("/stations")
    @Operation(summary = "获取所有充电站")
    public ResponseEntity<ApiResponse<List<ChargingStation>>> getAllStations() {
        List<ChargingStation> stations = stationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    @PostMapping("/stations")
    @Operation(summary = "创建充电站")
    public ResponseEntity<ApiResponse<ChargingStation>> createStation(
            @RequestBody ChargingStation station) {
        ChargingStation created = stationService.create(station);
        return ResponseEntity.ok(ApiResponse.success("充电站创建成功", created));
    }

    @PutMapping("/stations/{id}")
    @Operation(summary = "更新充电站信息")
    public ResponseEntity<ApiResponse<ChargingStation>> updateStation(
            @PathVariable(name = "id") Long id,
            @RequestBody ChargingStation station) {
        ChargingStation updated = stationService.update(id, station);
        return ResponseEntity.ok(ApiResponse.success("充电站更新成功", updated));
    }

    @DeleteMapping("/stations/{id}")
    @Operation(summary = "删除充电站及关联设备")
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable(name = "id") Long id) {
        stationService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("充电站已删除", null));
    }

    // 充电桩管理

    @PostMapping("/piles")
    @Operation(summary = "创建充电桩")
    public ResponseEntity<ApiResponse<ChargingPile>> createPile(
            @RequestParam(name = "stationId") Long stationId,
            @RequestBody ChargingPile pile) {
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new RuntimeException("充电站不存在"));
        pile.setStation(station);
        ChargingPile created = pileRepository.save(pile);
        return ResponseEntity.ok(ApiResponse.success("充电桩创建成功", created));
    }

    @PutMapping("/piles/{id}")
    @Operation(summary = "更新充电桩状态")
    public ResponseEntity<ApiResponse<ChargingPile>> updatePile(
            @PathVariable(name = "id") Long id,
            @RequestBody ChargingPile pileData) {
        ChargingPile pile = pileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("充电桩不存在"));

        if (pileData.getStatus() != null)
            pile.setStatus(pileData.getStatus());
        if (pileData.getPileType() != null)
            pile.setPileType(pileData.getPileType());

        ChargingPile updated = pileRepository.save(pile);
        return ResponseEntity.ok(ApiResponse.success("充电桩更新成功", updated));
    }

    // 车位管理

    @PostMapping("/spots")
    @Operation(summary = "创建车位")
    public ResponseEntity<ApiResponse<ParkingSpot>> createSpot(
            @RequestParam(name = "pileId") Long pileId,
            @RequestBody ParkingSpot spot) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new RuntimeException("充电桩不存在"));
        spot.setPile(pile);
        ParkingSpot created = spotRepository.save(spot);
        return ResponseEntity.ok(ApiResponse.success("车位创建成功", created));
    }

    @PutMapping("/spots/{id}")
    @Operation(summary = "更新车位信息")
    public ResponseEntity<ApiResponse<ParkingSpot>> updateSpot(
            @PathVariable(name = "id") Long id,
            @RequestBody ParkingSpot spotData) {
        ParkingSpot spot = spotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        if (spotData.getStatus() != null)
            spot.setStatus(spotData.getStatus());
        if (spotData.getPricePerHour() != null)
            spot.setPricePerHour(spotData.getPricePerHour());

        ParkingSpot updated = spotRepository.save(spot);
        return ResponseEntity.ok(ApiResponse.success("车位更新成功", updated));
    }

    // 预约与订单查询

    @GetMapping("/reservations")
    @Operation(summary = "获取所有预约记录")
    public ResponseEntity<ApiResponse<List<Reservation>>> getAllReservations() {
        List<Reservation> reservations = reservationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/orders")
    @Operation(summary = "获取所有订单记录")
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        List<Order> orders = orderService.findAll();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
