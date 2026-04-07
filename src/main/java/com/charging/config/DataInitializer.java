package com.charging.config;

import com.charging.entity.*;
import com.charging.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据初始化组件
 * 仅初始化系统用户，充电站数据由用户登录时按位置自动导入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

        private final UserRepository userRepository;
        private final ChargingStationRepository stationRepository;
        private final PasswordEncoder passwordEncoder;

        @Override
        @Transactional
        public void run(String... args) {
                if (userRepository.count() > 0 || stationRepository.count() > 0) {
                        log.info("检测到数据库已有数据，跳过初始化步骤。");
                        return;
                }

                log.info("开始初始化系统基础数据...");
                initUsers();
                log.info("数据初始化全部完成!");
        }

        private void initUsers() {
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

                User systemUser = User.builder()
                                .username("_system")
                                .password(passwordEncoder.encode("SYSTEM_NO_LOGIN"))
                                .realName("系统模拟用户")
                                .role(-1)
                                .status(0)
                                .build();
                userRepository.save(systemUser);
        }
}
