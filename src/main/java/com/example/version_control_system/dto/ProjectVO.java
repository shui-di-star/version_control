package com.example.version_control_system.dto;

import com.example.version_control_system.entity.Project;

/** 项目视图：项目信息 + 当前用户在该项目的角色 + 创建者名称。 */
public record ProjectVO(Long id, String name, String description, Long ownerId, String ownerName, String myRole) {

    public static ProjectVO from(Project p, String myRole, String ownerName) {
        return new ProjectVO(p.getId(), p.getName(), p.getDescription(), p.getOwnerId(), ownerName, myRole);
    }
}
