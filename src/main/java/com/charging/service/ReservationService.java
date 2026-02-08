package com.charging.service;

import com.charging.dto.ReservationDTO;
import com.charging.dto.ReservationRequest;
import com.charging.entity.Order;
import com.charging.entity.ParkingSpot;
import com.charging.entity.Reservation;
import com.charging.entity.User;
import com.charging.repository.OrderRepository;
import com.charging.repository.ParkingSpotRepository;
import com.charging.repository.ReservationRepository;
import com.charging.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 使用悲观锁查询车位，防止并发预约
        ParkingSpot spot = parkingSpotRepository.findByIdForUpdate(request.getSpotId())
                .orElseThrow(() -> new RuntimeException("车位不存在"));

        // 检查车位是否可用 (status=0表示空闲)
        if (spot.getStatus() != 0) {
            throw new RuntimeException("该车位当前不可预约");
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

        return reservationRepository.save(reservation);
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
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation checkIn(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        if (reservation.getStatus() != 1) {
            throw new RuntimeException("当前状态无法签到");
        }

        reservation.setStatus(2); // 使用中
        reservation.setActualArrivalTime(java.time.LocalDateTime.now());

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

        reservation.setStatus(3); // 已完成
        reservation.setActualLeaveTime(LocalDateTime.now());

        // 更新车位状态
        ParkingSpot spot = reservation.getSpot();
        spot.setStatus(0); // 空闲
        parkingSpotRepository.save(spot);

        Reservation saved = reservationRepository.save(reservation);

        // 自动创建订单
        createOrderFromReservation(saved);

        return saved;
    }

    private void createOrderFromReservation(Reservation reservation) {
        // 检查是否已有订单
        Optional<Order> existingOrder = orderRepository.findByReservationId(reservation.getId());
        if (existingOrder.isPresent()) {
            return; // 已存在订单，跳过
        }

        ParkingSpot spot = reservation.getSpot();

        // 计算费用
        LocalDateTime start = reservation.getActualArrivalTime() != null
                ? reservation.getActualArrivalTime()
                : reservation.getStartTime();
        LocalDateTime end = reservation.getActualLeaveTime() != null
                ? reservation.getActualLeaveTime()
                : reservation.getEndTime();

        long minutes = Duration.between(start, end).toMinutes();
        long hours = (minutes + 59) / 60; // 向上取整到小时
        if (hours < 1)
            hours = 1;

        BigDecimal parkingFee = spot.getPricePerHour() != null
                ? spot.getPricePerHour().multiply(BigDecimal.valueOf(hours))
                : BigDecimal.ZERO;

        BigDecimal serviceFee = spot.getServiceFee() != null ? spot.getServiceFee() : BigDecimal.ZERO;
        BigDecimal totalAmount = parkingFee.add(serviceFee);

        // 生成订单号
        String orderNo = "ORD" + System.currentTimeMillis() +
                String.format("%04d", (int) (Math.random() * 10000));

        Order order = Order.builder()
                .orderNo(orderNo)
                .user(reservation.getUser())
                .reservation(reservation)
                .parkingFee(parkingFee)
                .chargingFee(BigDecimal.ZERO)
                .serviceFee(serviceFee)
                .totalAmount(totalAmount)
                .paymentStatus(0)
                .status(1) // 待支付
                .build();

        orderRepository.save(order);
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
