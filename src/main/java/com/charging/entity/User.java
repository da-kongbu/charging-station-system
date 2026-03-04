package com.charging.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 系统用户实体类 (JPA Entity)
 * 
 * 作用：映射数据库的 `users` 核心账号表。
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录全网唯一用户名
     */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /**
     * 登录密码（数据库中必须存放哈希加密后的摘要，如 BCrypt 产物）
     */
    @Column(nullable = false)
    private String password;

    /**
     * 绑定的手机号 (也可以用作短信验证码登录)
     */
    @Column(unique = true, length = 20)
    private String phone;

    /**
     * 绑定的找回密码备用邮箱
     */
    @Column(length = 100)
    private String email;

    /**
     * 默认爱车车牌号
     */
    @Column(name = "car_plate", length = 20)
    private String carPlate;

    /**
     * KYC 实名认证姓名
     */
    @Column(name = "real_name", length = 50)
    private String realName;

    /**
     * 核心权限控制标志位：
     * 0-可以下单的普通老百姓，1-可以登录后台管理端的老板或物业
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer role = 0;

    /**
     * 账号健康状态：
     * 0-因为逃单或违规被永久封号拉黑禁用，1-一切正常
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
