package com.example.version_control_system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 项目级授权校验器，供无法用 {@link RequireProjectRole} 声明式解析 projectId 的场景
 * （如 {@code /api/entities/{id}/...}、{@code /api/assets/{id}/...} 需先由资源反查 projectId）手动调用。
 */
@Component
public class ProjectAuthorizer {

    private final ProjectMemberMapper projectMemberMapper;

    public ProjectAuthorizer(ProjectMemberMapper projectMemberMapper) {
        this.projectMemberMapper = projectMemberMapper;
    }

    /** 校验当前用户在 projectId 具备 ≥ required 的角色；SuperAdmin 放行，否则不足抛 AccessDenied。 */
    public void require(Long projectId, ProjectRole required) {
        AuthUser current = SecurityUtils.getCurrentUser();
        if (current.isSuperAdmin()) {
            return;
        }
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, current.userId()));
        if (member == null) {
            throw new AccessDeniedException("非项目成员");
        }
        ProjectRole actual;
        try {
            actual = ProjectRole.valueOf(member.getRole());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccessDeniedException("项目角色不足");
        }
        if (!actual.satisfies(required)) {
            throw new AccessDeniedException("项目角色不足");
        }
    }
}
