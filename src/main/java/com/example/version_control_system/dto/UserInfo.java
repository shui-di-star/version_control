package com.example.version_control_system.dto;

import com.example.version_control_system.entity.User;

/** 当前用户信息（不含敏感字段）。 */
public record UserInfo(Long id, String username, String email, String displayName, String systemRole) {

    public static UserInfo from(User u) {
        return new UserInfo(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getSystemRole());
    }
}
