package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 实体模板创建/更新请求。
 * <p>{@code fieldSchema} 为 JSON 字符串，结构见 §3.3：{@code {"fields":[{key,label,type,...}]}}，
 * 由 Service 校验合法性。</p>
 */
public record EntityTemplateRequest(
        @NotBlank @Size(max = 64) String name,
        String fieldSchema) {
}
