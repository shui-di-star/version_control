package com.example.version_control_system.security;

/**
 * 认证主体：存入 Spring Security 上下文，供 Controller/切面获取当前用户。
 *
 * @param userId     用户 ID
 * @param username   登录名
 * @param systemRole 全局角色（SUPER_ADMIN / USER）
 */
public record AuthUser(Long userId, String username, String systemRole) {

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(systemRole);
    }
}
