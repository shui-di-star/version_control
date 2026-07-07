package com.example.version_control_system.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记写操作方法，由 {@link OperationLogAspect} 在方法成功返回后记录到 t_operation_log。
 * <p>{@code action} 如 CREATE_ENTITY；{@code targetType} 如 ENTITY。projectId 从名为 projectId 的
 * 参数解析，userId 取当前登录用户，targetId 从返回结果的 id 提取，detail 为返回结果 JSON 快照。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    String action();

    String targetType();
}
