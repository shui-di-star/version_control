package com.example.version_control_system.dto;

import com.example.version_control_system.entity.Relation;

/** 语义关系视图。 */
public record RelationVO(Long id, Long projectId, Long templateId,
                         Long fromEntityId, Long toEntityId, String remark) {

    public static RelationVO from(Relation r) {
        return new RelationVO(r.getId(), r.getProjectId(), r.getTemplateId(),
                r.getFromEntityId(), r.getToEntityId(), r.getRemark());
    }
}
