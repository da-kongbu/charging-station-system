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
 * 预约管理服务
 *
 * 作用：处理预约创建、取消、签到、签退以及预约记录查询。
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final UserService userService;
    // 预约签退后自动生成订单
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
     * 创建预约
     */
    @Transactional
    public Reservation create(Long userId, ReservationRequest request) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 使用悲观锁避免同一车位被重复预约
        ParkingSpot spot = parkingSpotRepository.findByIdForUpdate(request.getSpotId())
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        if (spot.getStatus() == 0) {
            throw new RuntimeException("该车位处于维护或离线状态，暂停服务");
        }

        // 检查预约时间段冲突
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                request.getSpotId(),
                request.getStartTime(),
                request.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException("该时间段车位已被预约");
        }

        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new RuntimeException("结束时间必须晚于开始时间");
        }
        long hours = Duration.between(request.getStartTime(), request.getEndTime()).toHours();
        if (hours > 12) {
            throw new RuntimeException("单次预约时长不能超过12小时");
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .spot(spot)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .carPlate(request.getCarPlate() != null ? request.getCarPlate() : user.getCarPlate())
                .remark(request.getRemark())
                .status(1)
                .build();

        Reservation saved = reservationRepository.save(reservation);

        // 将车位状态更新为预约中
        spot.setStatus(2);
        parkingSpotRepository.save(spot);

        return saved;
    }

    /**
     * 取消预约
     */
    @Transactional
    public Reservation cancel(Long id, Long userId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权取消该预约");
        }

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前状态不支持取消预约");
        }

        reservation.setStatus(0);
        Reservation saved = reservationRepository.save(reservation);

        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1);
        parkingSpotRepository.save(spot);

        return saved;
    }

    /**
     * 预约签到
     */
    @Transactional
    public Reservation checkIn(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前状态不支持签到");
        }

        LocalDateTime now = LocalDateTime.now();

        // 允许在预约开始前5分钟内签到
        LocalDateTime earliestCheckIn = reservation.getStartTime().minusMinutes(5);
        if (now.isBefore(earliestCheckIn)) {
            throw new RuntimeException("未到签到时间，最早可于 "
                    + reservation.getStartTime().minusMinutes(5).toLocalTime() + " 开始签到");
        }

        if (now.isAfter(reservation.getEndTime())) {
            throw new RuntimeException("预约已过期，无法签到");
        }

        reservation.setStatus(2);
        reservation.setActualArrivalTime(now);

        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(3);
        parkingSpotRepository.save(spot);

        return reservationRepository.save(reservation);
    }

    /**
     * 预约签退并生成订单
     */
    @Transactional
    public Reservation checkOut(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (reservation.getStatus() != 2) {
            throw new RuntimeException("当前状态不支持签退");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaveTime = now.isAfter(reservation.getEndTime())
                ? reservation.getEndTime()
                : now;

        reservation.setStatus(3);
        reservation.setActualLeaveTime(leaveTime);

        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1);
        parkingSpotRepository.save(spot);

        Reservation saved = reservationRepository.save(reservation);

        // 签退完成后自动生成订单
        orderService.createFromReservation(saved.getId());

        return saved;
    }

    /**
     * 把数据提取为前端所需要的实体
     */
    private ReservationDTO convertToDTO(Reservation reservation) {
        ParkingSpot spot = reservation.getSpot();
        var pile = spot.getPile();
        var station = pile.getStation();

        // 预估充电价格
        long hours = Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (hours < 1)
            hours = 1;
        BigDecimal estimatedCost = spot.getPricePerHour() != null
                ? spot.getPricePerHour().multiply(BigDecimal.valueOf(hours))
                : BigDecimal.ZERO;
        // 四层连表嵌套：预约->车位->充电桩->充电站
        return ReservationDTO.builder()
                .id(reservation.getId())

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
