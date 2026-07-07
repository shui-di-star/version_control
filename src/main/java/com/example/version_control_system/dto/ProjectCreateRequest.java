package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建项目请求。 */
public record ProjectCreateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description) {
}
