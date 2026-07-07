package com.example.version_control_system.dto;

import com.example.version_control_system.entity.SimEntity;

/** 实体详情视图。 */
public record EntityVO(Long id, Long projectId, Long templateId, Long parentId, String name,
                       String status, Integer isMilestone, String remark, String attributes,
                       Long parentRelationTemplateId, String parentRelationRemark) {

    public static EntityVO from(SimEntity e) {
        return new EntityVO(e.getId(), e.getProjectId(), e.getTemplateId(), e.getParentId(),
                e.getName(), e.getStatus(), e.getIsMilestone(), e.getRemark(), e.getAttributes(),
                e.getParentRelationTemplateId(), e.getParentRelationRemark());
    }
}
