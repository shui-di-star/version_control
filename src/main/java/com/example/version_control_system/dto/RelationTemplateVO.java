package com.example.version_control_system.dto;

import com.example.version_control_system.entity.RelationTemplate;

/** 关系模板视图。 */
public record RelationTemplateVO(Long id, Long projectId, String name, Integer directed,
                                 String lineStyle, String allowedFrom, String allowedTo) {

    public static RelationTemplateVO from(RelationTemplate t) {
        return new RelationTemplateVO(t.getId(), t.getProjectId(), t.getName(), t.getDirected(),
                t.getLineStyle(), t.getAllowedFrom(), t.getAllowedTo());
    }
}
