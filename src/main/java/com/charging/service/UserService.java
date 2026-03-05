package com.charging.service;

import com.charging.dto.RegisterRequest;
import com.charging.entity.User;
import com.charging.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 基础用户管理中心服务
 * 
 * 作用：处理 C 端主账户实体的增删查改基本功能，包含最关键的安全密保防线设置——Bcrypt密码加盐散列持久化。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 由注入的 Spring Bean `BCryptPasswordEncoder` 自动担纲实现，
    // 用于对用户前台传上来的明文执行高强度安全加密以及密码检验验原操作。
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * 注册
     */
    @Transactional
    public User register(RegisterRequest request) {
        // [防御线] 检测核心字段防碰撞：确保系统里登录 ID 不重复打架
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("账号名已被注册");
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("您名下已有该手机号关联账号");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("此电子邮箱当前已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                // ==最重要的一步== 丢进粉碎机加密（永远不会在数据库明文暴露 123456）
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .carPlate(request.getCarPlate())
                .realName(request.getRealName())
                .role(0) // Default 凡人开局
                .status(1) // 满血在线无封号
                .build();

        return userRepository.save(user);
    }

    /**
     * 在 "我的资料" 卡片中自主维护更变普通周边属性
     */
    @Transactional
    public User update(Long id, User updateData) {
        //从数据库里根据id读取用户数据
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 更新可以被更新且有修改项的资料区块
        if (updateData.getPhone() != null)
            user.setPhone(updateData.getPhone());
        if (updateData.getEmail() != null)
            user.setEmail(updateData.getEmail());
        if (updateData.getCarPlate() != null)
            user.setCarPlate(updateData.getCarPlate());
        if (updateData.getRealName() != null)
            user.setRealName(updateData.getRealName());

        return userRepository.save(user);
    }

    /**
     * 针对非常敏感的关键密码修改建立特殊隔离管道
     */
    //加事务锁
    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        //根据id搜索用户
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 由于盐机制，明文密码即便长一样散列出的密文也可能相异，必须用指定的 matches() 解构验核真假
        //使用matches判断是否一致
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("密码输入错误！");
        }

        // 只有验核真身对牌成功了，才给换新密文锁心挂回门上存起
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 管理员在后台发下的一道封杀禁令
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到该用户"));
        user.setStatus(status); // 把如 0 传进去，让他之后登录时抛出禁止异常
        userRepository.save(user);
    }

    @Transactional
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}
