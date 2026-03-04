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
 * 面向 C 端用户的充电场站信息拉取控制器
 * 
 * 作用：当用户打开小程序或者微信端时，需要在首页地图上画点，或者搜索周边可用充电桩，
 * 这类高频大批量的查询接口都在这里提供服务。这些接通常也是无需强制登录就可以调阅浏览的（类似大众点评看店）。
 */
@RestController
@RequestMapping("/api/stations") // 主入口 /api/stations
@RequiredArgsConstructor
@Tag(name = "周边充电站广场", description = "提供检索、浏览、获悉场地实时可用状态坑位的公开信息")
public class ChargingStationController {

    private final ChargingStationService stationService;
    private final ParkingSpotService parkingSpotService;

    /**
     * 【GET】首次加载 App 时瀑布流或者大头针要拉取的主站表
     */
    @GetMapping
    @Operation(summary = "获取所有开放对外营业的充电站大名单列表")
    public ResponseEntity<ApiResponse<List<ChargingStationDTO>>> getAllStations() {
        // 注意：只返回 Available (没有倒闭和内部停业的) 的公开干净 DTO，保护非公字段
        List<ChargingStationDTO> stations = stationService.findAllAvailable();
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    /**
     * 【GET】用户点击地图上某一个大头针或者在列表点某一行进去看到的详细介绍页面
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取某一个充电站的详情及介绍信息")
    public ResponseEntity<ApiResponse<ChargingStationDTO>> getStation(@PathVariable(name = "id") Long id) {
        ChargingStationDTO station = stationService.findById(id)
                .orElseThrow(() -> new RuntimeException("请求的该充电站编号已失效或不存在"));
        return ResponseEntity.ok(ApiResponse.success(station));
    }

    /**
     * 【GET】最上方搜索条根据关键字查找 (智能模糊匹配 / 城市限制)
     */
    @GetMapping("/search")
    @Operation(summary = "复合查询搜索充电站")
    public ResponseEntity<ApiResponse<List<ChargingStationDTO>>> searchStations(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "city", required = false) String city) {

        List<ChargingStationDTO> stations;
        // 如果打了字搜索场地、企业名称
        if (keyword != null && !keyword.isEmpty()) {
            stations = stationService.searchByKeyword(keyword);
        } else if (city != null && !city.isEmpty()) {
            // 如果点选了同城下拉列表过滤
            stations = stationService.findByCity(city);
        } else {
            // 两者都没传直接视为全量刷新查
            stations = stationService.findAllAvailable();
        }
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    /**
     * 【GET】子路由：下穿透找属于当前选定站名下所有的充电桩信息（包含各自分布的功率型号展示）
     */
    @GetMapping("/{id}/piles")
    @Operation(summary = "获取这个站点名册里注册的所有物理桩分布列")
    public ResponseEntity<ApiResponse<List<ChargingPile>>> getStationPiles(@PathVariable(name = "id") Long id) {
        List<ChargingPile> piles = stationService.getPilesByStationId(id);
        return ResponseEntity.ok(ApiResponse.success(piles));
    }

    /**
     * 【GET】核心挑选车位：列出本站底下现在没在被人霸占和在充的全部空闲空地坑位
     */
    @GetMapping("/{id}/spots")
    @Operation(summary = "当前时点直取本站能马上停进去的空闲车位")
    public ResponseEntity<ApiResponse<List<ParkingSpot>>> getAvailableSpots(@PathVariable(name = "id") Long id) {
        List<ParkingSpot> spots = parkingSpotService.findAvailableByStationId(id);
        return ResponseEntity.ok(ApiResponse.success(spots));
    }

    /**
     * 【GET】智能预约探索雷达：
     * 如果我是想搜明早8点-10点能留给我的车位（排雷历史契约记录）
     */
    @GetMapping("/spots/available")
    @Operation(summary = "查询预指定未来时间段夹缝内可锁的空余车位群")
    public ResponseEntity<ApiResponse<List<ParkingSpot>>> getAvailableSpotsForTime(
            @RequestParam(name = "startTime") LocalDateTime startTime,
            @RequestParam(name = "endTime") LocalDateTime endTime) {
        // 调用复杂碰撞算法交接检查
        List<ParkingSpot> spots = parkingSpotService.findAvailableSpotsForTime(startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(spots));
    }
}
