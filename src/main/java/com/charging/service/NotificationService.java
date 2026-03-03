package com.charging.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 消息通知服务 (模拟)
 */
@Service
@Slf4j
public class NotificationService {

    /**
     * 发送短信
     */
    @Async
    public void sendSms(String phone, String message) {
        // 模拟发送延迟
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        log.info("【模拟短信发送】To: {}, Content: {}", phone, message);
    }

    /**
     * 发送邮件
     */
    @Async
    public void sendEmail(String to, String subject, String content) {
        // 模拟发送延迟
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        log.info("【模拟邮件发送】To: {}, Subject: {}\nContent: {}", to, subject, content);
    }
}
