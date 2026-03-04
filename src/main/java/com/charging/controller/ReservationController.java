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
 * 个人出行业务调度器 (车道闸)
 * 
 * 作用：这是全站唯一且最集中的处理 C 端个人车主关于定车位、进站登记扫码、离场断电拔枪四大核心操作的 API 层。
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "车辆预约调度台", description = "统辖个人扫码入场离场的整条生命周期流水线")
public class ReservationController {

    private final ReservationService reservationService; // 统辖排队锁的业务调度层
    private final UserService userService; // 提供个人校验等

    // ==================
    // 自身历史记录调取与日历画盘用
    // ==================

    @GetMapping
    @Operation(summary = "拉出我的订车位流水总本子")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getMyReservations(
            @AuthenticationPrincipal UserDetails userDetails) {
        // 利用切面隔离权限，绝不靠前端传 ID 查人历史，而是去认证体系 Security Holder 里提取本车主
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户身份失效"));

        List<ReservationDTO> reservations = reservationService.findByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查看某张单独的历史停车券回单详情")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservation(@PathVariable(name = "id") Long id) {
        ReservationDTO reservation = reservationService.findById(id)
                .orElseThrow(() -> new RuntimeException("这票子失效或者无效编码"));
        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    @GetMapping("/spot/{spotId}/date/{date}")
    @Operation(summary = "【前台时间排班表专用】拉取指定某个坑位在某一天的已被预订碎段记录用于画红黄条")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getReservationsBySpotAndDate(
            @PathVariable(name = "spotId") Long spotId,
            @PathVariable(name = "date") LocalDate date) {
        // 例：前端画甘特图，需要知道今天这个坑被哪几个人断断续续霸占了
        List<ReservationDTO> reservations = reservationService.findBySpotAndDate(spotId, date);
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }

    // ==================
    // 核心生命流转四大操作：锁坑 -> 反悔 / (接枪签到 -> 拔枪结账出场)
    // ==================

    @PostMapping
    @Operation(summary = "【步骤一】向系统正式发起指定某个时段某个车位的长连接锁定期")
    public ResponseEntity<ApiResponse<Reservation>> createReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReservationRequest request) { // 经过基础 @NotNull 表面合法性检验漏斗的 Json

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("系统检测不到投表人实体"));

        // 调用底层的【悲观排他防撕裂】上锁服务层去办大事
        Reservation reservation = reservationService.create(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("🎉手速真快，抢坑落单成功！恭迎大驾", reservation));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "【分支废弃路线】我临时有事车不开了，撒锁放位")
    public ResponseEntity<ApiResponse<Reservation>> cancelReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable(name = "id") Long id) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("无权进行释放"));

        Reservation reservation = reservationService.cancel(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("已和平解约退订成功", reservation));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "【步骤二】车子驶入白线内，扫码打卡激活通电连线")
    public ResponseEntity<ApiResponse<Reservation>> checkIn(@PathVariable(name = "id") Long id) {
        // 会在后端记录当前的签到时间戳来启动计费物理时间校准引擎
        Reservation reservation = reservationService.checkIn(id);
        return ResponseEntity.ok(ApiResponse.success("打卡完成电流已激活，可前往爱车前挡风查看看进度", reservation));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "【步骤末】长按拔枪断电，强制清算结束这个行程并释放地皮资源")
    public ResponseEntity<ApiResponse<Reservation>> checkOut(@PathVariable(name = "id") Long id) {
        // 调用离场服务，它会自动派发跑在后边的计费流水总账生成任务投递给财务 (OrderService)
        Reservation reservation = reservationService.checkOut(id);
        return ResponseEntity.ok(ApiResponse.success("电已安全切断成功离场！账单请移步订单页交钱", reservation));
    }
}
