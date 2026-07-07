package com.example.version_control_system.dto;

/** 登录成功返回：JWT + 基本用户信息。 */
public record LoginResponse(String token, Long userId, String username, String systemRole) {
}
