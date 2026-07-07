package com.example.version_control_system.dto;

import java.util.ArrayList;
import java.util.List;

/** 嵌套树节点 VO（由平铺 CTE 行组装）。 */
public record EntityTreeNode(Long id, Long parentId, String name, Long templateId,
                             String status, Integer isMilestone,
                             Long parentRelationTemplateId, String parentRelationRemark,
                             String attributes,
                             List<EntityTreeNode> children) {

    public static EntityTreeNode of(EntityTreeRow row) {
        return new EntityTreeNode(row.id(), row.parentId(), row.name(), row.templateId(),
                row.status(), row.isMilestone(), row.parentRelationTemplateId(), row.parentRelationRemark(),
                row.attributes(), new ArrayList<>());
    }
}
