package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 实体更新请求（名称/备注/属性）。父子结构变更不走此接口。
 */
public record EntityUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String remark,
        String attributes) {
}
