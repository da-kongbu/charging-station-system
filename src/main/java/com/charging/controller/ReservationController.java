package com.charging.controller;

import com.charging.dto.ApiResponse;
import com.charging.dto.ReservationDTO;
import com.charging.dto.ReservationRequest;
import com.charging.entity.Reservation;
import com.charging.entity.User;
import com.charging.service.ReservationService;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 预约控制器
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "预约管理", description = "车位预约相关接口")
public class ReservationController {

    private final ReservationService reservationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "获取当前用户的预约列表")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getMyReservations(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        List<ReservationDTO> reservations = reservationService.findByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取预约详情")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservation(@PathVariable(name = "id") Long id) {
        ReservationDTO reservation = reservationService.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    @GetMapping("/spot/{spotId}/date/{date}")
    @Operation(summary = "获取车位指定日期的预约情况")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getReservationsBySpotAndDate(
            @PathVariable(name = "spotId") Long spotId,
            @PathVariable(name = "date") LocalDate date) {
        List<ReservationDTO> reservations = reservationService.findBySpotAndDate(spotId, date);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @PostMapping
    @Operation(summary = "创建预约")
    public ResponseEntity<ApiResponse<Reservation>> createReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReservationRequest request) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Reservation reservation = reservationService.create(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("预约成功", reservation));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消预约")
    public ResponseEntity<ApiResponse<Reservation>> cancelReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Reservation reservation = reservationService.cancel(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("取消成功", reservation));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "签到（到达充电站）")
    public ResponseEntity<ApiResponse<Reservation>> checkIn(@PathVariable(name = "id") Long id) {
        Reservation reservation = reservationService.checkIn(id);
        return ResponseEntity.ok(ApiResponse.success("签到成功", reservation));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "结束使用")
    public ResponseEntity<ApiResponse<Reservation>> checkOut(@PathVariable(name = "id") Long id) {
        Reservation reservation = reservationService.checkOut(id);
        return ResponseEntity.ok(ApiResponse.success("已结束使用", reservation));
    }
}
