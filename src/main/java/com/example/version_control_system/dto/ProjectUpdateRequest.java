package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 更新项目请求。 */
public record ProjectUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description) {
}
