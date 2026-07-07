package com.example.version_control_system.dto;

import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.User;

/** 成员视图：成员在项目中的角色 + 用户基本信息。 */
public record MemberVO(Long userId, String username, String displayName, String role) {

    public static MemberVO from(ProjectMember m, User u) {
        return new MemberVO(
                m.getUserId(),
                u == null ? null : u.getUsername(),
                u == null ? null : u.getDisplayName(),
                m.getRole());
    }
}
