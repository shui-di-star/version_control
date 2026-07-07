package com.example.version_control_system.log;

import com.example.version_control_system.common.JsonUtils;
import com.example.version_control_system.common.Result;
import com.example.version_control_system.entity.OperationLog;
import com.example.version_control_system.mapper.OperationLogMapper;
import com.example.version_control_system.security.AuthUser;
import com.example.version_control_system.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * {@link LogOperation} 切面：写操作成功返回后记录 t_operation_log。
 * <p>projectId 取名为 projectId 的方法参数；userId 取当前用户；targetId/detail 从返回的
 * {@link Result#getData()} 提取（有 getId() 则取之，detail 存 data 的 JSON 快照）。
 * 日志写入失败只告警、不影响主流程。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogMapper operationLogMapper;
    private final JsonUtils jsonUtils;

    public OperationLogAspect(OperationLogMapper operationLogMapper, JsonUtils jsonUtils) {
        this.operationLogMapper = operationLogMapper;
        this.jsonUtils = jsonUtils;
    }

    @Around("@annotation(com.example.version_control_system.log.LogOperation)")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            writeLog(pjp, result);
        } catch (Exception e) {
            log.warn("操作日志记录失败，不影响主流程: {}", e.getMessage());
        }
        return result;
    }

    private void writeLog(ProceedingJoinPoint pjp, Object result) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        LogOperation annotation = method.getAnnotation(LogOperation.class);

        AuthUser current = SecurityUtils.getCurrentUser();
        Long projectId = resolveProjectId(method, pjp.getArgs());
        Object data = result instanceof Result<?> r ? r.getData() : result;

        OperationLog entry = new OperationLog();
        entry.setProjectId(projectId);
        entry.setUserId(current.userId());
        entry.setAction(annotation.action());
        entry.setTargetType(annotation.targetType());
        entry.setTargetId(extractId(data));
        entry.setDetail(data == null ? null : jsonUtils.toJson(data));
        operationLogMapper.insert(entry);
    }

    private Long resolveProjectId(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if ("projectId".equals(params[i].getName()) && args[i] instanceof Number n) {
                return n.longValue();
            }
        }
        return null;
    }

    private Long extractId(Object data) {
        if (data == null) {
            return null;
        }
        try {
            Method getId = data.getClass().getMethod("id");
            Object v = getId.invoke(data);
            return v instanceof Number n ? n.longValue() : null;
        } catch (Exception ignored) {
            // 无 id() 访问器（如 record 组件名不同或 Void），返回 null
            return null;
        }
    }
}
