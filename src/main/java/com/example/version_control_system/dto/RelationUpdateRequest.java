package com.example.version_control_system.dto;

import jakarta.validation.constraints.Size;

/** 语义关系更新请求：可改关系模板和备注。 */
public record RelationUpdateRequest(
        Long templateId,
        @Size(max = 500) String remark) {
}
