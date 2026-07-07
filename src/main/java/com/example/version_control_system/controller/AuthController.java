package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.LoginRequest;
import com.example.version_control_system.dto.LoginResponse;
import com.example.version_control_system.dto.RegisterRequest;
import com.example.version_control_system.dto.UserInfo;
import com.example.version_control_system.security.AuthUser;
import com.example.version_control_system.security.SecurityUtils;
import com.example.version_control_system.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 认证接口：注册/登录公开，me/logout 需认证。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserInfo> me() {
        AuthUser current = SecurityUtils.getCurrentUser();
        return Result.success(authService.getUserInfo(current.userId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        AuthUser current = SecurityUtils.getCurrentUser();
        authService.logout(current.userId());
        return Result.success();
    }
}
