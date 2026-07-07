package com.example.version_control_system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注方法参数，显式指明它是 projectId，供 {@link RequireProjectRole} 切面解析。
 * <p>参数可以是 {@code Long}（直接就是 projectId），或一个对象——此时从其
 * {@code getProjectId()} 取值（请求体场景）。</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectId {
}
