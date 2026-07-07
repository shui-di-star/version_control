package com.example.version_control_system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在需要项目级授权的方法上：要求当前用户在目标项目具备 ≥ {@link #value()} 的角色。
 * <p>projectId 解析优先级：先看被 {@code @ProjectId} 标注的方法参数，
 * 否则取名为 {@code pid} 或 {@code projectId} 的参数。SuperAdmin 一律放行。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectRole {

    ProjectRole value();
}
