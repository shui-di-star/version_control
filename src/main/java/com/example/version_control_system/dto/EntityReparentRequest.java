package com.example.version_control_system.dto;

import jakarta.validation.constraints.Size;

/**
 * 重选父节点请求。
 * <p>{@code parentId} 为 null 表示变为根节点；非 null 时 {@code parentRelationTemplateId} 必填。</p>
 */
public record EntityReparentRequest(
        Long parentId,
        Long parentRelationTemplateId,
        @Size(max = 512) String parentRelationRemark) {
}
