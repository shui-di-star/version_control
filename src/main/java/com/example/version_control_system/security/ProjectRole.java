package com.example.version_control_system.security;

/**
 * 项目级角色，含等级序：VIEWER &lt; EDITOR &lt; ADMIN。
 * <p>用于 {@code @RequireProjectRole} 的"角色 ≥ 要求"比较。</p>
 */
public enum ProjectRole {

    VIEWER(1),
    EDITOR(2),
    ADMIN(3);

    private final int level;

    ProjectRole(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /** 当前角色是否满足所需角色（等级 ≥）。 */
    public boolean satisfies(ProjectRole required) {
        return this.level >= required.level;
    }
}
