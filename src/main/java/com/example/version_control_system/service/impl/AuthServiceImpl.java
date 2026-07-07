package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.LoginRequest;
import com.example.version_control_system.dto.LoginResponse;
import com.example.version_control_system.dto.RegisterRequest;
import com.example.version_control_system.dto.UserInfo;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.mapper.UserMapper;
import com.example.version_control_system.security.JwtService;
import com.example.version_control_system.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public UserInfo register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setSystemRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return UserInfo.from(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getSystemRole());
    }

    @Override
    public UserInfo getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserInfo.from(user);
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        User update = new User();
        update.setId(userId);
        update.setTokenInvalidBefore(LocalDateTime.now());
        userMapper.updateById(update);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
