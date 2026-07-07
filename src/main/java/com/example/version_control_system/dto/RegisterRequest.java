package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 注册请求。 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @Size(max = 128) String email,
        @Size(max = 64) String displayName) {
}
