package com.example.version_control_system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 语义关系创建请求。仅存非父子语义关系；父子迭代关系由 t_entity.parent_id 表达，绝不写入此处。
 */
public record RelationCreateRequest(
        @NotNull Long templateId,
        @NotNull Long fromEntityId,
        @NotNull Long toEntityId,
        @Size(max = 500) String remark) {
}
