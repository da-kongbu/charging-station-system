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
 * 用户管理服务
 *
 * 作用：处理用户注册、资料维护和密码修改等操作。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 用于密码加密与密码校验
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
        // 校验用户名、手机号和邮箱是否重复
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("账号名已被注册");
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .carPlate(request.getCarPlate())
                .realName(request.getRealName())
                .role(0)
                .status(1)
                .build();

        return userRepository.save(user);
    }

    /**
     * 更新用户基础资料
     */
    @Transactional
    public User update(Long id, User updateData) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

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
     * 修改用户密码
     */
    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到该用户"));
        user.setStatus(status);
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
