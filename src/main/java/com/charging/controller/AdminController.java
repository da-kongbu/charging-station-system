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
 * 平台总控后台管理控制器 (Controller Layer)
 * 
 * 作用：为老板/物业/管理员提供的 PC 端后台数据接口，包含了最高权限的删改查操作集合。
 * 它统筹了人、站、桩、坑、单的所有管理功能。
 */
@RestController // @Controller + @ResponseBody，表明此类下所有路由方法的返回值都会自动转为 JSON 格式返回给前端
@RequestMapping("/api/admin") // 路由前缀：规定此类所有接口都必须以 /api/admin/... 开头
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 【权限高压线】：Spring Security 切面拦截，没有 ADMIN 权限角色令牌的人试图访问时直接抛 403 Forbidden
@Tag(name = "管理后台", description = "管理员专用接口") // Swagger/SpringDoc 的 API 接口分类文档注解
public class AdminController {

    // 依赖注入所需要的全部业务大管家 (Service) 和部分直接透传的数据访问层 (Repository)
    private final UserService userService;
    private final ChargingStationService stationService;
    private final OrderService orderService;
    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;

    // =========================================================
    // 统计大屏数据源聚合区 (首页 Dashboard 数据)
    // =========================================================

    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        // 1. 静态存量盘点统计：到底系统里有多少人、有多少个充电站营业中、多少坑位
        dashboard.put("totalUsers", userService.findAll().size());
        dashboard.put("totalStations", stationRepository.count());
        dashboard.put("totalPiles", pileRepository.count());
        dashboard.put("totalSpots", spotRepository.count());

        // 2. 动态增量实时流水统计：当天业绩怎么样？
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now();

        // 当日完单数及总营业流水（包含预收电费、车位服务费之和）
        Long todayOrders = orderService.countCompletedOrders(todayStart, todayEnd);
        BigDecimal todayRevenue = orderService.calculateRevenue(todayStart, todayEnd);

        // 今天刚进来的排队预约量
        long todayReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(todayStart))
                .count();

        dashboard.put("todayOrders", todayOrders);
        dashboard.put("todayReservations", todayReservations);
        dashboard.put("todayRevenue", todayRevenue);

        // 3. 跑马灯组件：抓取近十笔交易单明细，供首页滚动动态显示效果
        List<Order> recentOrders = orderService.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // 按时间倒序排
                .limit(10) // 只需要前 10 个数据
                .toList();
        dashboard.put("recentOrders", recentOrders);

        // 封装在 ApiResponse 里发送给前端，HTTP 状态码给 200 OK
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    // =========================================================
    // C 端上帝账户管理（封号、解禁）
    // =========================================================

    @GetMapping("/users")
    @Operation(summary = "获取所有用户列表")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAll();
        // 实际上线环境中这里应该做 Pageable 分页，不然 100 万个用户查全表会直接把网卡内存打爆
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "更新用户状态，如拉黑/解封")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable(name = "id") Long id, // 从 URL 路径获取要制裁的那个倒霉蛋的 ID
            @RequestParam(name = "status") Integer status) { // 从 URL http://.../?status=0 里拿传参
        userService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("用户状态已更新", null));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "注销/清退删除用户")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable(name = "id") Long id) {
        userService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }

    // =========================================================
    // 充电场/站区基础物理物料管理 (增删改场地信息)
    // =========================================================

    @GetMapping("/stations")
    @Operation(summary = "获取系统内登记注册的所有充电站列表")
    public ResponseEntity<ApiResponse<List<ChargingStation>>> getAllStations() {
        // 直接返回底层的 List<ChargingStation>，带全套原始配置用于后台管理表格呈现
        List<ChargingStation> stations = stationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    @PostMapping("/stations")
    @Operation(summary = "投资方新建立并登记一个充电站")
    public ResponseEntity<ApiResponse<ChargingStation>> createStation(
            @RequestBody ChargingStation station) { // @RequestBody 会把前端 Http Body 送上来的 JSON 字符串反序列化包装成 Bean
        ChargingStation created = stationService.create(station);
        return ResponseEntity.ok(ApiResponse.success("充电站创建成功", created));
    }

    @PutMapping("/stations/{id}")
    @Operation(summary = "修改维护充电站的基本信息资料")
    public ResponseEntity<ApiResponse<ChargingStation>> updateStation(
            @PathVariable(name = "id") Long id,
            @RequestBody ChargingStation station) {
        ChargingStation updated = stationService.update(id, station);
        return ResponseEntity.ok(ApiResponse.success("充电站更新成功", updated));
    }

    @DeleteMapping("/stations/{id}")
    @Operation(summary = "拆除下线报废一整个充电站及连带附属设备")
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable(name = "id") Long id) {
        stationService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("充电站已删除", null));
    }

    // =========================================================
    // 零散充电桩/设备单体运维管理
    // =========================================================

    @PostMapping("/piles")
    @Operation(summary = "为现有场站接线增设部署一台新的充电桩")
    public ResponseEntity<ApiResponse<ChargingPile>> createPile(
            @RequestParam(name = "stationId") Long stationId, // 挂载到哪个电站名下
            @RequestBody ChargingPile pile) { // 设备自己的铭牌参数
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new RuntimeException("所给定的充电站本体查无此记录"));
        pile.setStation(station); // 硬接轨
        ChargingPile created = pileRepository.save(pile);
        return ResponseEntity.ok(ApiResponse.success("充电桩创建成功", created));
    }

    @PutMapping("/piles/{id}")
    @Operation(summary = "更新运维修理某台充电桩的软硬件状态")
    public ResponseEntity<ApiResponse<ChargingPile>> updatePile(
            @PathVariable(name = "id") Long id,
            @RequestBody ChargingPile pileData) {
        ChargingPile pile = pileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("充电桩不存在"));

        // 允许仅仅变更局部需要的内容，比如把它从离线置为空闲
        if (pileData.getStatus() != null)
            pile.setStatus(pileData.getStatus());
        if (pileData.getPileType() != null)
            pile.setPileType(pileData.getPileType());

        ChargingPile updated = pileRepository.save(pile);
        return ResponseEntity.ok(ApiResponse.success("充电桩检修更新成功", updated));
    }

    // =========================================================
    // 更细粒度：停车位坑位管理 (划线计费策略调整)
    // =========================================================

    @PostMapping("/spots")
    @Operation(summary = "在地上喷漆圈定新分化出的车位")
    public ResponseEntity<ApiResponse<ParkingSpot>> createSpot(
            @RequestParam(name = "pileId") Long pileId,
            @RequestBody ParkingSpot spot) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new RuntimeException("这根充电桩不存在，无法依附"));
        spot.setPile(pile);
        ParkingSpot created = spotRepository.save(spot);
        return ResponseEntity.ok(ApiResponse.success("车位创建成功", created));
    }

    @PutMapping("/spots/{id}")
    @Operation(summary = "更新车位的计费费率或者锁定维修")
    public ResponseEntity<ApiResponse<ParkingSpot>> updateSpot(
            @PathVariable(name = "id") Long id,
            @RequestBody ParkingSpot spotData) {
        ParkingSpot spot = spotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        if (spotData.getStatus() != null)
            spot.setStatus(spotData.getStatus());
        // 可以由后台人为涨价或者降价调整这个车位的防呆占位费费率
        if (spotData.getPricePerHour() != null)
            spot.setPricePerHour(spotData.getPricePerHour());

        ParkingSpot updated = spotRepository.save(spot);
        return ResponseEntity.ok(ApiResponse.success("车位策略更新成功", updated));
    }

    // =========================================================
    // 汇总审计查账列表用接口
    // =========================================================

    @GetMapping("/reservations")
    @Operation(summary = "获取所有历史的订坑留位记录表")
    public ResponseEntity<ApiResponse<List<Reservation>>> getAllReservations() {
        List<Reservation> reservations = reservationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/orders")
    @Operation(summary = "获取真金白银交易结案财务流水表")
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        List<Order> orders = orderService.findAll();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
