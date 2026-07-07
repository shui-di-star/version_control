package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录请求。 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
