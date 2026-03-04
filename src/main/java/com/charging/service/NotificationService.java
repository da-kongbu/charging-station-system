package com.charging.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 消息通知服务 (外联解耦组件)
 * 
 * 作用：模拟向车主的手机或者关联邮箱发送诸如"充电完成"、"扣费成功"等提醒。
 * 现实场景中这里会调用阿里云、腾讯云等第三方的 SMS/Mail API 接口。
 */
@Service
@Slf4j
public class NotificationService {

    /**
     * 发送文本短信
     * 
     * @Async 注解使得调用该方法会被扔进 Spring 的后台异步线程池，不会阻塞如“付款成功”这种主线程动作的时间。
     */
    @Async
    public void sendSms(String phone, String message) {
        // 模拟网络请求发送延迟
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("模拟短信发送中断", e);
        }
        log.info("【模拟短信发送成功】目标手机: {}, 下发内容: {}", phone, message);
    }

    /**
     * 发送电子邮件
     */
    @Async
    public void sendEmail(String to, String subject, String content) {
        // 模拟发送延迟
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("模拟邮件发送中断", e);
        }
        log.info("【模拟邮件发送成功】收件人: {}, \n主题: {}\n正文: {}", to, subject, content);
    }
}
