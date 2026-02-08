package com.charging.config;

import com.charging.entity.*;
import com.charging.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 数据初始化器 - 用于初始化测试数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

        private final UserRepository userRepository;
        private final ChargingStationRepository stationRepository;
        private final ChargingPileRepository pileRepository;
        private final ParkingSpotRepository spotRepository;
        private final PasswordEncoder passwordEncoder;

        @Override
        public void run(String... args) {
                // 检查是否已有数据
                if (userRepository.count() > 0) {
                        log.info("数据库已有数据，跳过初始化");
                        return;
                }

                log.info("开始初始化测试数据...");

                // 创建管理员用户
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

                // 创建普通用户
                User user = User.builder()
                                .username("user")
                                .password(passwordEncoder.encode("user123"))
                                .phone("13800000001")
                                .email("user@charging.com")
                                .realName("测试用户")
                                .carPlate("京A12345")
                                .role(0)
                                .status(1)
                                .build();
                userRepository.save(user);

                // 创建充电站1
                ChargingStation station1 = ChargingStation.builder()
                                .name("中关村充电站")
                                .address("北京市海淀区中关村大街1号")
                                .city("北京")
                                .district("海淀区")
                                .longitude(new BigDecimal("116.3252"))
                                .latitude(new BigDecimal("39.9836"))
                                .contact("010-12345678")
                                .businessHours("24小时营业")
                                .description("中关村核心区域大型充电站，配备快充和慢充设施")
                                .status(1)
                                .build();
                station1 = stationRepository.save(station1);

                // 创建充电站2
                ChargingStation station2 = ChargingStation.builder()
                                .name("望京充电站")
                                .address("北京市朝阳区望京街10号")
                                .city("北京")
                                .district("朝阳区")
                                .longitude(new BigDecimal("116.4716"))
                                .latitude(new BigDecimal("40.0028"))
                                .contact("010-87654321")
                                .businessHours("06:00-24:00")
                                .description("望京商业区充电站，提供快速充电服务")
                                .status(1)
                                .build();
                station2 = stationRepository.save(station2);

                // 为充电站1创建充电桩
                ChargingPile pile1_1 = ChargingPile.builder()
                                .station(station1)
                                .pileCode("ZGC-DC-001")
                                .pileType("DC")
                                .power(new BigDecimal("120"))
                                .voltage(new BigDecimal("750"))
                                .current(new BigDecimal("160"))
                                .brand("特来电")
                                .connectorType("国标DC")
                                .status(1)
                                .build();
                pile1_1 = pileRepository.save(pile1_1);

                ChargingPile pile1_2 = ChargingPile.builder()
                                .station(station1)
                                .pileCode("ZGC-AC-001")
                                .pileType("AC")
                                .power(new BigDecimal("7"))
                                .voltage(new BigDecimal("220"))
                                .current(new BigDecimal("32"))
                                .brand("星星充电")
                                .connectorType("国标AC")
                                .status(1)
                                .build();
                pile1_2 = pileRepository.save(pile1_2);

                // 为充电站2创建充电桩
                ChargingPile pile2_1 = ChargingPile.builder()
                                .station(station2)
                                .pileCode("WJ-DC-001")
                                .pileType("DC")
                                .power(new BigDecimal("180"))
                                .voltage(new BigDecimal("750"))
                                .current(new BigDecimal("240"))
                                .brand("国家电网")
                                .connectorType("国标DC")
                                .status(1)
                                .build();
                pile2_1 = pileRepository.save(pile2_1);

                // 创建车位
                ParkingSpot spot1 = ParkingSpot.builder()
                                .pile(pile1_1)
                                .spotCode("ZGC-A01")
                                .spotType("STANDARD")
                                .pricePerHour(new BigDecimal("5.00"))
                                .serviceFee(new BigDecimal("0.80"))
                                .status(0)
                                .build();
                spotRepository.save(spot1);

                ParkingSpot spot2 = ParkingSpot.builder()
                                .pile(pile1_1)
                                .spotCode("ZGC-A02")
                                .spotType("STANDARD")
                                .pricePerHour(new BigDecimal("5.00"))
                                .serviceFee(new BigDecimal("0.80"))
                                .status(0)
                                .build();
                spotRepository.save(spot2);

                ParkingSpot spot3 = ParkingSpot.builder()
                                .pile(pile1_2)
                                .spotCode("ZGC-B01")
                                .spotType("STANDARD")
                                .pricePerHour(new BigDecimal("3.00"))
                                .serviceFee(new BigDecimal("0.60"))
                                .status(0)
                                .build();
                spotRepository.save(spot3);

                ParkingSpot spot4 = ParkingSpot.builder()
                                .pile(pile2_1)
                                .spotCode("WJ-A01")
                                .spotType("LARGE")
                                .pricePerHour(new BigDecimal("8.00"))
                                .serviceFee(new BigDecimal("1.00"))
                                .status(0)
                                .build();
                spotRepository.save(spot4);

                log.info("测试数据初始化完成!");
                log.info("管理员账号: admin / admin123");
                log.info("普通用户账号: user / user123");
        }
}
