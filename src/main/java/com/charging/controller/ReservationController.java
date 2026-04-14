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
 * 预约管理接口
 *
 * 作用：处理用户预约、取消、签到和签退等操作。
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "预约管理", description = "提供预约全流程管理接口")
public class ReservationController {

    private final ReservationService reservationService;
    private final UserService userService;

    // ==================
    // 自身历史记录调取与日历画盘用
    // ==================

    @GetMapping
    @Operation(summary = "获取当前用户的预约记录")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getMyReservations(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户身份失效"));

        List<ReservationDTO> reservations = reservationService.findByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取预约详情")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        ReservationDTO reservation = reservationService.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));
        // 越权校验：只能查看自己的预约
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!reservation.getUserId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权访问该预约"));
        }
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    @GetMapping("/spot/{spotId}/date/{date}")
    @Operation(summary = "获取指定车位在指定日期的预约记录")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getReservationsBySpotAndDate(
            @PathVariable(name = "spotId") Long spotId,
            @PathVariable(name = "date") LocalDate date) {
        List<ReservationDTO> reservations = reservationService.findBySpotAndDate(spotId, date);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    // ==================
    // 预约生命周期相关操作
    // ==================

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
        return ResponseEntity.ok(ApiResponse.success("预约已取消", reservation));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "预约签到")
    public ResponseEntity<ApiResponse<Reservation>> checkIn(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Reservation reservation = reservationService.checkIn(id);
        // 越权校验
        if (!reservation.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权操作该预约"));
        }
        return ResponseEntity.ok(ApiResponse.success("签到成功", reservation));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "预约签退")
    public ResponseEntity<ApiResponse<Reservation>> checkOut(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Reservation reservation = reservationService.checkOut(id);
        // 越权校验
        if (!reservation.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("无权操作该预约"));
        }
        return ResponseEntity.ok(ApiResponse.success("签退成功，请前往订单页支付", reservation));
    }
}
