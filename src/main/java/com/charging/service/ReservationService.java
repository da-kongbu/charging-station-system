package com.charging.service;

import com.charging.dto.ReservationDTO;
import com.charging.dto.ReservationRequest;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.entity.User;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 车位预约核心调度中心
 * 
 * 作用：整个业务中最命门的地方，负责解决用户发起停车位预见期的分配和资源锁防争抢任务。
 * 这里涵盖了基于悲观锁（Pessimistic Lock）进行车位拦截、跨时间片段冲突检测等技术手段。
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final UserService userService;
    // 注入订单总管，因为结束停车的一瞬间要自动联动拉起跑计费账单流
    private final OrderService orderService;

    public List<ReservationDTO> findByUserId(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ReservationDTO> findBySpotAndDate(Long spotId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        return reservationRepository.findBySpotIdAndDateRange(spotId, startOfDay, endOfDay).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<ReservationDTO> findById(Long id) {
        return reservationRepository.findById(id)
                .map(this::convertToDTO);
    }

    /**
     * 用户点击 "立刻预约" 发起的抢占锁车位请求
     * 
     * @Transactional 保证操作原子性：下发派单+锁地库必须共进退
     */
    @Transactional
    public Reservation create(Long userId, ReservationRequest request) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // [防御核心 1] 悲观锁防超卖：
        // 这一句查询带上了 FOR UPDATE（底层实现），保证在同一微秒有两个人同时点这一个坑，
        // 数据库会排队处理，强制一方被抛出异常，绝不让一桩两充的情况发成事故。
        ParkingSpot spot = parkingSpotRepository.findByIdForUpdate(request.getSpotId())
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        // [防御核心 2] 拦阻硬伤：0 表示那个坑位的传感器掉线或者坑被大水淹了，绝不外借
        if (spot.getStatus() == 0) {
            throw new RuntimeException("该车位处于维护或离线状态，暂停服务");
        }

        // [防御核心 3] 业务时段冲突检测：
        // 执行一段自定义的 SQL 去数据库找历史记录，看看别人承诺霸占这个车位的起止期，
        // 和本用户当前请求的 [startTime, endTime] 是不是在时间线重叠交织打架了。
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                request.getSpotId(),
                request.getStartTime(),
                request.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException("非常抱歉，该时间段这个黄金坑位已被其他车主捷足先登");
        }

        // 防弱智前端传值拦截
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new RuntimeException("结束时间必须晚于开始时间，时光不能倒流");
        }
        long hours = Duration.between(request.getStartTime(), request.getEndTime()).toHours();
        if (hours > 12) {
            throw new RuntimeException("为防僵尸车长期恶意占充电不走，单次预约最高限锁12小时");
        }

        // 大关全部通过，盖章签字落库，生成有效预约票根
        Reservation reservation = Reservation.builder()
                .user(user)
                .spot(spot)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .carPlate(request.getCarPlate() != null ? request.getCarPlate() : user.getCarPlate())
                .remark(request.getRemark())
                .status(1) // 标记这单 1-待车开过来使用
                .build();

        Reservation saved = reservationRepository.save(reservation);

        // [关键] 联动把真实物理数据库记录的坑位涂成 2(预约黄)，这样别的页面就不会把它当空缺车位推荐了
        spot.setStatus(2);
        parkingSpotRepository.save(spot);

        return saved;
    }

    /**
     * 主动放弃毁弃契约释放占坑
     */
    @Transactional
    public Reservation cancel(Long id, Long userId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        // 极权隔离：不是你抢的，你不能撤销它，防恶意篡改他人表单
        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("您无权越界取消他人的预约行程单");
        }

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前流程节点不支持退回（必须在待使用前）");
        }

        reservation.setStatus(0); // 废了这单
        Reservation saved = reservationRepository.save(reservation);

        // 坑位颜色洗牌变回 1(空闲绿)，重新对外放号兜售
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1);
        parkingSpotRepository.save(spot);

        return saved;
    }

    /**
     * 车开到了，扫码确认使用：签到（打卡）通电
     */
    @Transactional
    public Reservation checkIn(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("查无此单"));

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("该单不能签到（可能已入场，或已过期）");
        }

        LocalDateTime now = LocalDateTime.now();

        // 留 5 分钟的宽限期让人能提前一点点接电，防止干等时间到卡点
        LocalDateTime earliestCheckIn = reservation.getStartTime().minusMinutes(5);
        if (now.isBefore(earliestCheckIn)) {
            throw new RuntimeException("早到了，还未到您的预约时段。请稍等或提前改签，最早可于 "
                    + reservation.getStartTime().minusMinutes(5).toLocalTime() + " 点击入场通电");
        }

        // 来太晚直接拦截报废掉
        if (now.isAfter(reservation.getEndTime())) {
            throw new RuntimeException("您的预约已因超期而彻底违规失效关闭，系统无法继续提供签到");
        }

        reservation.setStatus(2); // 2-打入使用中高地
        reservation.setActualArrivalTime(now); // 记录下用来算计钱的起表起点

        // 让该车位正式进入工作状态火爆占用（红）
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(3);
        parkingSpotRepository.save(spot);

        return reservationRepository.save(reservation);
    }

    /**
     * 用户拔出插头，确认离开结算
     */
    @Transactional
    public Reservation checkOut(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("单号丢失"));

        if (reservation.getStatus() != 2) {
            throw new RuntimeException("不在电网工作连线状态没法断电结账");
        }

        LocalDateTime now = LocalDateTime.now();
        // 取个巧防薅羊毛：如果你霸坑时间已经由于什么卡顿之类的滑出了你当承诺结束的时间，
        // 会由于你失信，依然以你的预计截止时间死线作为卡口，或者如果有超期费惩罚的话走罚款流
        LocalDateTime leaveTime = now.isAfter(reservation.getEndTime())
                ? reservation.getEndTime()
                : now;

        reservation.setStatus(3); // 3-成功出场完成这趟旅途
        reservation.setActualLeaveTime(leaveTime);

        // 坑位让出来挂牌招租卖身
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1);
        parkingSpotRepository.save(spot);

        Reservation saved = reservationRepository.save(reservation);

        // ********** [极度关键枢纽点] **********
        // 所有状态流转完并恢复车位元气后，立刻强行呼叫兄弟部门【财务派单 OrderService】
        // 传递这笔 ID 去那边把动态金额轧好生成出来逼迫客户交钱。
        orderService.createFromReservation(saved.getId());

        return saved;
    }

    /**
     * 将繁重臃肿带着双向指针链且包含大量子对象的记录提取过滤转化成前端极简表。
     */
    private ReservationDTO convertToDTO(Reservation reservation) {
        ParkingSpot spot = reservation.getSpot();
        var pile = spot.getPile();
        var station = pile.getStation();

        // 这里仅为了纯前端界面列表页的简单展示（并非实际打表数额）算个数额占位
        long hours = Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (hours < 1)
            hours = 1;
        BigDecimal estimatedCost = spot.getPricePerHour() != null
                ? spot.getPricePerHour().multiply(BigDecimal.valueOf(hours))
                : BigDecimal.ZERO;

        return ReservationDTO.builder()
                .id(reservation.getId())
                // 击穿四层连表嵌套：预约->车位->桩柱->场站
                .userId(reservation.getUser().getId())
                .username(reservation.getUser().getUsername())
                .spotId(spot.getId())
                .spotCode(spot.getSpotCode())
                .pileId(pile.getId())
                .pileCode(pile.getPileCode())
                .pileType(pile.getPileType())
                .pilePower(pile.getPower())
                .stationId(station.getId())
                .stationName(station.getName())
                .stationAddress(station.getAddress())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .carPlate(reservation.getCarPlate())
                .status(reservation.getStatus())
                .statusText(ReservationDTO.getStatusText(reservation.getStatus()))
                .estimatedCost(estimatedCost)
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
