package com.charging.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 申请发起预约请求传入的参数 DTO (Data Transfer Object)
 * 
 * 作用：接收来源于前端移动端、小程序或者 Web 端发出的 "新建一笔看中并下单" 的车位资源申请。
 * Spring MVC 借助 Validation 对提交的关键数据执行第一道过滤关切。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    /**
     * 想预约的目标车位的具体主键 (系统必须知道想占用哪个坑)
     * 利用 @NotNull 强制必须传参
     */
    @NotNull(message = "车位ID不能为空")
    private Long spotId;

    /**
     * 消费者预期的停车充电起算时间（不能早于当前时间）
     */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /**
     * 消费者预期的充电离园时间（必需晚于开始时间，该时间差用来测算总费用和占用时档）
     */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /**
     * 本次将要通过道闸和占位的具体车牌号码
     * (可选或通过前端下拉选择关联，便于自动进出场识别)
     */
    private String carPlate;

    /**
     * 消费者附加留言，类似如需要人工指导、或者有设备自带的转接头之类的备忘录
     */
    private String remark;
}
