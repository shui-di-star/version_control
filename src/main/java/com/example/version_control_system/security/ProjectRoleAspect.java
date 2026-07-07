package com.example.version_control_system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * {@link RequireProjectRole} 切面：校验当前用户在目标项目的角色。
 * <p>顺序（决策/§4.3）：SuperAdmin 直接放行；否则解析 projectId，查 t_project_member，
 * 校验角色 ≥ 要求，不足或非成员抛 FORBIDDEN。</p>
 */
@Aspect
@Component
public class ProjectRoleAspect {

    private final ProjectMemberMapper projectMemberMapper;

    public ProjectRoleAspect(ProjectMemberMapper projectMemberMapper) {
        this.projectMemberMapper = projectMemberMapper;
    }

    @Around("@annotation(com.example.version_control_system.security.RequireProjectRole)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RequireProjectRole annotation = method.getAnnotation(RequireProjectRole.class);
        ProjectRole required = annotation.value();

        AuthUser current = SecurityUtils.getCurrentUser();
        if (current.isSuperAdmin()) {
            return pjp.proceed();
        }

        Long projectId = resolveProjectId(method, pjp.getArgs());
        if (projectId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法解析 projectId");
        }

        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, current.userId()));
        if (member == null) {
            throw new AccessDeniedException("非项目成员");
        }

        ProjectRole actual = parseRole(member.getRole());
        if (actual == null || !actual.satisfies(required)) {
            throw new AccessDeniedException("项目角色不足");
        }
        return pjp.proceed();
    }

    /** 解析 projectId：优先 @ProjectId 标注的参数，否则名为 pid/projectId 的参数。 */
    private Long resolveProjectId(Method method, Object[] args) {
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        // 1. @ProjectId 标注
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation a : paramAnnotations[i]) {
                if (a instanceof ProjectId) {
                    return extractProjectId(args[i]);
                }
            }
        }
        // 2. 名为 pid / projectId 的参数
        String[] names = method.getParameters() != null
                ? java.util.Arrays.stream(method.getParameters()).map(java.lang.reflect.Parameter::getName).toArray(String[]::new)
                : new String[0];
        for (int i = 0; i < names.length; i++) {
            if ("pid".equals(names[i]) || "projectId".equals(names[i])) {
                return extractProjectId(args[i]);
            }
        }
        return null;
    }

    /** 参数值可能是 Long/String（直接是 id），或带 getProjectId() 的对象。 */
    private Long extractProjectId(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof Long l) {
            return l;
        }
        if (arg instanceof Number n) {
            return n.longValue();
        }
        if (arg instanceof String s) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // 对象：反射调用 getProjectId()
        try {
            Method getter = arg.getClass().getMethod("getProjectId");
            Object v = getter.invoke(arg);
            return v == null ? null : ((Number) v).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private ProjectRole parseRole(String role) {
        try {
            return ProjectRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
