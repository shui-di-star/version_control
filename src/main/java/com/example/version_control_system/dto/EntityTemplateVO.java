package com.example.version_control_system.dto;

import com.example.version_control_system.entity.EntityTemplate;

/** 实体模板视图。 */
public record EntityTemplateVO(Long id, Long projectId, String name, String fieldSchema) {

    public static EntityTemplateVO from(EntityTemplate t) {
        return new EntityTemplateVO(t.getId(), t.getProjectId(), t.getName(), t.getFieldSchema());
    }
}
