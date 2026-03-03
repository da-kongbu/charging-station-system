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
 * 充电模拟服务 - 模拟充电桩实时数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargingSimulationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationRepository reservationRepository;
    private final Random random = new Random();

    // 内存中缓存每个订单的当前充电进度，用于模拟连续性
    private final Map<Long, Integer> socCache = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> energyCache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 2000) // 每2秒推送一次
    public void simulateCharging() {
        // 查询所有正在使用中的预约 (status=2)
        List<Reservation> activeReservations = reservationRepository.findByStatus(2);

        for (Reservation reservation : activeReservations) {
            pushChargingStatus(reservation);
        }

        // 清理非活动订单的缓存
        socCache.keySet().removeIf(id -> activeReservations.stream().noneMatch(r -> r.getId().equals(id)));
        energyCache.keySet().removeIf(id -> activeReservations.stream().noneMatch(r -> r.getId().equals(id)));
    }

    private void pushChargingStatus(Reservation reservation) {
        Long id = reservation.getId();

        // 模拟电压 (220V +/- 5V)
        double voltageVal = 220 + (random.nextDouble() * 10 - 5);
        BigDecimal voltage = BigDecimal.valueOf(voltageVal).setScale(1, RoundingMode.HALF_UP);

        // 模拟电流 (32A +/- 2A)
        double currentVal = 32 + (random.nextDouble() * 4 - 2);
        BigDecimal current = BigDecimal.valueOf(currentVal).setScale(1, RoundingMode.HALF_UP);

        // 计算功率 (kW)
        BigDecimal power = voltage.multiply(current).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        // 模拟 SOC (State Of Charge)
        // 初始SOC假设为20%，每2秒增加1% (为了演示效果加快速度)
        int currentSoc = socCache.getOrDefault(id, 20);
        if (currentSoc < 100) {
            // 95%概率增加，模拟真实波动
            if (random.nextDouble() > 0.1) {
                currentSoc++;
            }
        }
        socCache.put(id, currentSoc);

        // 模拟已充能量 (kWh)
        BigDecimal currentEnergy = energyCache.getOrDefault(id, BigDecimal.ZERO);
        // Energy += Power (kW) * Time (2s = 2/3600 h)
        BigDecimal addedEnergy = power.multiply(BigDecimal.valueOf(2.0 / 3600.0));
        currentEnergy = currentEnergy.add(addedEnergy);
        energyCache.put(id, currentEnergy);

        // 剩余时间 (简单估算：(100-SOC) / Speed)
        // 假设每分钟充x%，简单给个动态值
        int remainingTime = (100 - currentSoc) * 2; // 粗略估算

        ChargingStatusDTO statusDTO = ChargingStatusDTO.builder()
                .reservationId(id)
                .status(currentSoc >= 100 ? "COMPLETED" : "CHARGING")
                .voltage(voltage)
                .current(current)
                .power(power)
                .soc(currentSoc)
                .remainingTime(remainingTime)
                .chargedEnergy(currentEnergy.setScale(2, RoundingMode.HALF_UP))
                .build();

        // 发送消息到前端
        // 订阅地址: /topic/charging/{reservationId}
        messagingTemplate.convertAndSend("/topic/charging/" + id, statusDTO);
    }
}
