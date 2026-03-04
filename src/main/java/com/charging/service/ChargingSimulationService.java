package com.charging.service;

import com.charging.dto.ChargingStatusDTO;
import com.charging.entity.Reservation;
import com.charging.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 充电过程模拟服务 (核心特色功能)
 * 
 * 作用：在没有真实硬件充电桩对接的情况下，本类通过 Spring Boot 的定时任务(@Scheduled)
 * 模拟硬件在持续不断向后台上传物理电压、电流、电量的过程。
 * 它结合 WebSocket 技术，每 2 秒向前端手机推送一次进度更新，从而让前端跑出进度条效果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargingSimulationService {

    // Spring 的 WebSocket 消息推送模板，负责把消息顶到前端
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationRepository reservationRepository;
    private final Random random = new Random();

    // 内存中缓存每个在充订单的当前充电进度百分比，用于保证百分比是往上叠加的
    private final Map<Long, Integer> socCache = new ConcurrentHashMap<>();

    // 内存中缓存每个在充订单的当前已充入电能度数（kWh）
    private final Map<Long, BigDecimal> energyCache = new ConcurrentHashMap<>();

    /**
     * 核心定时调度方法：每隔 2000 毫秒（2秒）被线程池自动触发一次
     * 只有在使用 @EnableScheduling 注解开启定时任务后才会生效
     */
    @Scheduled(fixedRate = 2000)
    public void simulateCharging() {
        // 1. 查询数据库中所有状态为 2（正在查枪使用中）的预约单
        List<Reservation> activeReservations = reservationRepository.findByStatus(2);

        // 2. 遍历每个单子，向这台车对应的手机用户下发最新的电量数据
        for (Reservation reservation : activeReservations) {
            pushChargingStatus(reservation);
        }

        // 3. 清理缓存防内存泄漏：如果有些单子已经不是状态2了（拔枪了），那么把它们从内存缓存中剔除
        socCache.keySet().removeIf(id -> activeReservations.stream().noneMatch(r -> r.getId().equals(id)));
        energyCache.keySet().removeIf(id -> activeReservations.stream().noneMatch(r -> r.getId().equals(id)));
    }

    /**
     * 负责虚构一套物理数据并推向 WebSocket 的核心方法
     */
    private void pushChargingStatus(Reservation reservation) {
        Long id = reservation.getId();

        // 1. 模拟市电电压 (基准 220V，上下轻微波动 +/- 5V 显得真实)
        double voltageVal = 220 + (random.nextDouble() * 10 - 5);
        BigDecimal voltage = BigDecimal.valueOf(voltageVal).setScale(1, RoundingMode.HALF_UP);

        // 2. 模拟充电枪输出电流 (基准慢充线 32A，上下波动 +/- 2A)
        double currentVal = 32 + (random.nextDouble() * 4 - 2);
        BigDecimal current = BigDecimal.valueOf(currentVal).setScale(1, RoundingMode.HALF_UP);

        // 3. 计算实时功率 (P = UI / 1000 转换为千瓦 kW)
        BigDecimal power = voltage.multiply(current).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        // 4. 模拟当前电池电量 SOC (State Of Charge)
        // 第一次默认给个 20%的底座电量。现实中这个是从车的 CAN 总线读取到的
        int currentSoc = socCache.getOrDefault(id, 20);
        if (currentSoc < 100) {
            // 我们不希望 2 秒就固定涨 1%，所以设定 90% 的概率让它这次涨一点，模拟真实不规律上窜的情况
            if (random.nextDouble() > 0.1) {
                currentSoc++;
            }
        }
        socCache.put(id, currentSoc);

        // 5. 换算本次增加的充能度数 (W = P * T)
        BigDecimal currentEnergy = energyCache.getOrDefault(id, BigDecimal.ZERO);
        // 定时器是2秒执行一次，所以 T = 2秒 = (2/3600) 小时
        BigDecimal addedEnergy = power.multiply(BigDecimal.valueOf(2.0 / 3600.0));
        currentEnergy = currentEnergy.add(addedEnergy); // 累加进总量
        energyCache.put(id, currentEnergy);

        // 6. 估算剩余所需充电时间（简单算法：剩余百分比 乘系数，假设1个点2分钟）
        int remainingTime = (100 - currentSoc) * 2;

        // 7. 组装专门下发给前端的 DTO
        ChargingStatusDTO statusDTO = ChargingStatusDTO.builder()
                .reservationId(id)
                .status(currentSoc >= 100 ? "COMPLETED" : "CHARGING") // 如果虚构到100%了，发个充满信号
                .voltage(voltage)
                .current(current)
                .power(power)
                .soc(currentSoc)
                .remainingTime(remainingTime)
                .chargedEnergy(currentEnergy.setScale(2, RoundingMode.HALF_UP))
                .build();

        // 8. 调用 STOMP 协议发布订阅模式。把组合好的对象发送给监听了 `/topic/charging/{订单号}` 频道的手机
        messagingTemplate.convertAndSend("/topic/charging/" + id, statusDTO);
    }
}
