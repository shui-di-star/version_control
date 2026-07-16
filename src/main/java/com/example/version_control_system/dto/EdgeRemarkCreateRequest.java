package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建连线备注请求。 */
public record EdgeRemarkCreateRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
