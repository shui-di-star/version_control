package com.example.version_control_system.dto;

/** 递归 CTE 树查询的平铺行投影（含 depth）。 */
public record EntityTreeRow(Long id, Long parentId, String name, Long templateId,
                            String status, Integer isMilestone, Integer depth,
                            Long parentRelationTemplateId, String parentRelationRemark,
                            String attributes) {
}
