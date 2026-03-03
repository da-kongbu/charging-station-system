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
 * 预约服务
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final UserService userService;
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

    @Transactional
    public Reservation create(Long userId, ReservationRequest request) {
        // 验证用户
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 使用悲观锁查询车位，防止并发预约
        ParkingSpot spot = parkingSpotRepository.findByIdForUpdate(request.getSpotId())
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        // 检查车位是否可用 (0=离线/故障，只有这种情况不能约)
        // 1=空闲, 2=预约中, 3=使用中 (这些状态只要时间不冲突都可以约)
        if (spot.getStatus() == 0) {
            throw new RuntimeException("该车位处于维护或离线状态，暂停服务");
        }

        // 检查时间冲突
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                request.getSpotId(),
                request.getStartTime(),
                request.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException("该时间段已被预约");
        }

        // 验证时间
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new RuntimeException("结束时间必须晚于开始时间");
        }

        // 检查预约时长不超过12小时
        long hours = Duration.between(request.getStartTime(), request.getEndTime()).toHours();
        if (hours > 12) {
            throw new RuntimeException("预约时长不能超过12小时");
        }

        // 创建预约
        Reservation reservation = Reservation.builder()
                .user(user)
                .spot(spot)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .carPlate(request.getCarPlate() != null ? request.getCarPlate() : user.getCarPlate())
                .remark(request.getRemark())
                .status(1) // 待使用
                .build();

        Reservation saved = reservationRepository.save(reservation);

        // 更新车位状态为 预约中 (2)
        spot.setStatus(2);
        parkingSpotRepository.save(spot);

        return saved;
    }

    @Transactional
    public Reservation cancel(Long id, Long userId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        // 验证是否是本人的预约
        if (!reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权取消此预约");
        }

        // 只有待使用状态可以取消
        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前状态无法取消");
        }

        reservation.setStatus(0); // 已取消
        Reservation saved = reservationRepository.save(reservation);

        // 更新车位状态为 空闲 (1)
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1);
        parkingSpotRepository.save(spot);

        return saved;
    }

    @Transactional
    public Reservation checkIn(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前状态无法签到");
        }

        LocalDateTime now = LocalDateTime.now();
        // 允许提前5分钟签到
        LocalDateTime earliestCheckIn = reservation.getStartTime().minusMinutes(5);
        if (now.isBefore(earliestCheckIn)) {
            throw new RuntimeException("还未到预约时间，最早可在 "
                    + reservation.getStartTime().minusMinutes(5).toLocalTime() + " 签到");
        }

        // 如果已经超过预约结束时间，不允许签到
        if (now.isAfter(reservation.getEndTime())) {
            throw new RuntimeException("预约已过期，无法签到");
        }

        reservation.setStatus(2); // 使用中
        reservation.setActualArrivalTime(now);

        // 更新车位状态
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(3); // 使用中
        parkingSpotRepository.save(spot);

        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation checkOut(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (reservation.getStatus() != 2) {
            throw new RuntimeException("当前状态无法结束");
        }

        LocalDateTime now = LocalDateTime.now();
        // 实际离开时间不能超过预约结束时间（超时按结束时间算）
        LocalDateTime leaveTime = now.isAfter(reservation.getEndTime())
                ? reservation.getEndTime()
                : now;

        reservation.setStatus(3); // 已完成
        reservation.setActualLeaveTime(leaveTime);

        // 更新车位状态
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(1); // 空闲
        parkingSpotRepository.save(spot);

        Reservation saved = reservationRepository.save(reservation);

        // 自动创建订单 (调用订单服务，内含计费逻辑)
        orderService.createFromReservation(saved.getId());

        return saved;
    }

    private ReservationDTO convertToDTO(Reservation reservation) {
        ParkingSpot spot = reservation.getSpot();
        var pile = spot.getPile();
        var station = pile.getStation();

        // 计算预估费用
        long hours = Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (hours < 1)
            hours = 1;
        BigDecimal estimatedCost = spot.getPricePerHour() != null
                ? spot.getPricePerHour().multiply(BigDecimal.valueOf(hours))
                : BigDecimal.ZERO;

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
