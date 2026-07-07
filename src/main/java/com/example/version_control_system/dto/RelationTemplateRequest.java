package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 关系模板创建/更新请求。
 * <p>{@code lineStyle}/{@code allowedFrom}/{@code allowedTo} 均为 JSON 字符串，
 * 由 Service 校验合法性；allowedFrom/allowedTo 为实体类型 id 数组，空=不限。</p>
 */
public record RelationTemplateRequest(
        @NotBlank @Size(max = 64) String name,
        Integer directed,
        String lineStyle,
        String allowedFrom,
        String allowedTo) {
}
