package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 实体创建请求。
 * <p>{@code attributes} 为 JSON 字符串，键对应模板 field_schema，由 Service 依模板校验。
 * {@code parentId} 可空=根节点；{@code parentId}/{@code templateId} 合法性由 Service 校验。</p>
 */
public record EntityCreateRequest(
        @NotNull Long templateId,
        Long parentId,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String remark,
        String attributes,
        Long parentRelationTemplateId,
        String parentRelationRemark) {
}
