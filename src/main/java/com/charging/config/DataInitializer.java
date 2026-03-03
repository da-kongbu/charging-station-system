package com.charging.config;

import com.charging.entity.*;
import com.charging.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 数据初始化组件 - 增强版
 * Backend Expert: Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

        private final UserRepository userRepository;
        private final ChargingStationRepository stationRepository;
        private final ChargingPileRepository pileRepository;
        private final ParkingSpotRepository spotRepository;
        private final ReservationRepository reservationRepository;
        private final PasswordEncoder passwordEncoder;

        private final Random random = new Random();

        @Override
        @Transactional
        public void run(String... args) {
                // 1. 安全检查：如果数据库已有用户或站点数据，绝对不执行初始化，防止覆盖
                if (userRepository.count() > 0 || stationRepository.count() > 0) {
                        log.info("检测到数据库已有数据，跳过初始化步骤。");
                        return;
                }

                log.info("开始初始化系统基础数据...");

                // 2. 初始化用户
                User admin = initUsers();

                // 3. 初始化演示用充电站 (北京)
                initStationData(admin);

                log.info("数据初始化全部完成!");
        }

        private User initUsers() {
                // 创建管理员
                User admin = User.builder()
                                .username("admin")
                                .password(passwordEncoder.encode("admin123"))
                                .phone("13800000000")
                                .email("admin@charging.com")
                                .realName("系统管理员")
                                .role(1)
                                .status(1)
                                .build();
                userRepository.save(admin);

                // 创建普通用户 (用于模拟预约)
                User user = User.builder()
                                .username("user")
                                .password(passwordEncoder.encode("user123"))
                                .phone("13800000001")
                                .email("user@charging.com")
                                .realName("测试用户")
                                .carPlate("京A88888")
                                .role(0)
                                .status(1)
                                .build();
                userRepository.save(user);

                return user; // 返回普通用户用于生成预约数据
        }

        private void initStationData(User mockUser) {
                // 创建一个北京的示例站点
                ChargingStation station = ChargingStation.builder()
                                .name("北京望京SOHO超级充电站")
                                .address("北京市朝阳区望京街10号")
                                .city("北京市")
                                .district("朝阳区")
                                .longitude(new BigDecimal("116.48105"))
                                .latitude(new BigDecimal("39.996794"))
                                .contact("010-88886666")
                                .businessHours("24小时营业")
                                .description("位于望京核心商业区，提供极速充电体验，周边餐饮配套齐全。")
                                .status(1)
                                .build();

                station = stationRepository.save(station);

                // 为该站点生成 5-10 个随机充电桩
                int pileCount = random.nextInt(6) + 5;
                for (int i = 0; i < pileCount; i++) {
                        createRandomPile(station, i + 1, mockUser);
                }

                log.info("已生成示例站点: {}，包含 {} 个充电桩", station.getName(), pileCount);
        }

        private void createRandomPile(ChargingStation station, int index, User mockUser) {
                // 生成随机桩编号: SOHO-001
                String pileNo = String.format("CP-%04d-%02d", station.getId(), index);
                boolean isFast = random.nextBoolean(); // 随机快慢充

                ChargingPile pile = ChargingPile.builder()
                                .station(station)
                                .pileCode(pileNo) // 桩编号
                                .pileType(isFast ? "DC_FAST" : "AC_SLOW")
                                .power(isFast ? new BigDecimal("120.0") : new BigDecimal("7.0"))
                                .voltage(new BigDecimal("220.0"))
                                .current(new BigDecimal("32.0"))
                                .brand(random.nextBoolean() ? "特来电" : "国家电网")
                                .connectorType("国标2015")
                                .status(1) // 默认桩是好的
                                .build();

                pile = pileRepository.save(pile);

                // 每个桩生成 1-2 个车位 (枪头)
                int spotCount = random.nextInt(2) + 1;
                for (int j = 0; j < spotCount; j++) {
                        createRandomSpot(pile, j + 1, mockUser);
                }
        }

        private void createRandomSpot(ChargingPile pile, int index, User mockUser) {
                // 生成车位编号: CP-001-A
                String spotNo = pile.getPileCode() + "-" + (char) ('A' + index - 1);

                ParkingSpot spot = ParkingSpot.builder()
                                .pile(pile)
                                .spotCode(spotNo)
                                .spotType("STANDARD")
                                .pricePerHour(new BigDecimal(random.nextInt(5) + 3)) // 3-8元/小时
                                .serviceFee(new BigDecimal("0.8"))
                                .status(1) // 初始默认为空闲(1)
                                .build();

                spot = spotRepository.save(spot);

                // === 核心逻辑：生成今日的随机预约时间条 ===
                // 这将决定前端看到的“有颜色的时间条”
                simulateDailyReservations(spot, mockUser);
        }

        /**
         * 为车位模拟生成今天的预约记录（时间条）
         * 同时根据当前时间是否被占用，修正车位的 status
         */
        /**
         * 核心修复：生成预约数据，并确保“车位状态”与“时间条”绝对一致
         */
        /**
         * 核心修复：生成预约数据，并确保“车位状态”与“时间条”绝对一致
         * 比例调整：40%预约中(黄), 40%占用中(红), 20%空闲(绿)
         */
        private void simulateDailyReservations(ParkingSpot spot, User user) {
                LocalDateTime now = LocalDateTime.now();
                List<Reservation> dailyReservations = new ArrayList<>();

                // === 掷骰子：决定这个车位现在的状态 ===
                double chance = random.nextDouble();

                if (chance < 0.4) {
                        // 【情况A：预约中 (黄)】 -> 必须生成一条“紧接着现在”的记录
                        // 逻辑：开始于未来 10-30分钟后，结束于未来
                        LocalDateTime start = now.plusMinutes(10 + random.nextInt(21));
                        LocalDateTime end = start.plusMinutes(60);

                        createReservation(user, spot, start, end, 1, dailyReservations); // status 1 = 待使用
                        spot.setStatus(2); // 2=预约中 (列表显示黄色)

                } else if (chance < 0.8) {
                        // 【情况B：占用中 (红)】 -> 必须生成一条“跨越当前时间”的记录
                        // 逻辑：开始于过去，结束于未来
                        LocalDateTime start = now.minusMinutes(30 + random.nextInt(60));
                        LocalDateTime end = now.plusMinutes(15 + random.nextInt(46));

                        createReservation(user, spot, start, end, 2, dailyReservations); // status 2 = 使用中
                        spot.setStatus(3); // 3=使用中 (列表显示红色)

                } else {
                        // 【情况C：空闲 (绿)】 -> 确保当前无记录
                        spot.setStatus(1); // 1=空闲 (列表显示绿色)
                }

                // 保存车位状态
                spotRepository.save(spot);

                // === 2. 填充干扰数据 (让时间条看起来丰富一点) ===
                // 尝试生成 2 个随机时间段
                LocalDate today = LocalDate.now();
                for (int i = 0; i < 2; i++) {
                        int randomHour = 8 + random.nextInt(14);
                        LocalDateTime start = LocalDateTime.of(today, LocalTime.of(randomHour, 0))
                                        .plusMinutes(random.nextInt(60));
                        LocalDateTime end = start.plusMinutes(60 + random.nextInt(60));

                        // 简单冲突检查 (不严谨，但够用)
                        boolean conflict = false;
                        for (Reservation existing : dailyReservations) {
                                if (start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime())) {
                                        conflict = true;
                                        break;
                                }
                        }
                        // 也不要覆盖当前时间 (如果当前是空闲)
                        if (spot.getStatus() == 1 && start.isBefore(now) && end.isAfter(now)) {
                                conflict = true;
                        }

                        if (!conflict) {
                                int status = 1; // 默认待使用
                                if (end.isBefore(now))
                                        status = 3; // 已完成
                                createReservation(user, spot, start, end, status, dailyReservations);
                        }
                }
        }

        // 抽离出来的通用方法，方便调用
        private void createReservation(User user, ParkingSpot spot, LocalDateTime start, LocalDateTime end, int status,
                        List<Reservation> list) {
                Reservation res = Reservation.builder()
                                .user(user)
                                .spot(spot)
                                .startTime(start)
                                .endTime(end)
                                .status(status)
                                .carPlate("京A" + random.nextInt(99999))
                                .createdAt(LocalDateTime.now().minusHours(1))
                                .build();

                // 如果是进行中或已完成，设置实际到达时间
                if (status == 2 || status == 3) {
                        res.setActualArrivalTime(start.plusMinutes(2));
                }

                reservationRepository.save(res);
                list.add(res);
        }
}
