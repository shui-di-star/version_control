package com.example.version_control_system.common;

/**
 * 业务错误码规范。
 * <p>
 * 约定：code=0 表示成功；非 0 为错误。分段：
 * <ul>
 *   <li>1xxx —— 通用/参数类错误</li>
 *   <li>2xxx —— 认证与鉴权</li>
 *   <li>3xxx —— 资源与业务规则</li>
 * </ul>
 */
public enum ResultCode {

    SUCCESS(0, "成功"),

    // 1xxx 通用
    BAD_REQUEST(1000, "请求参数错误"),
    VALIDATION_ERROR(1001, "参数校验失败"),
    INTERNAL_ERROR(1002, "服务器内部错误"),

    // 2xxx 认证与鉴权
    UNAUTHORIZED(2000, "未认证或登录已失效"),
    FORBIDDEN(2001, "无权限执行该操作"),

    // 3xxx 资源与业务规则
    NOT_FOUND(3000, "资源不存在"),
    CONFLICT(3001, "资源冲突"),
    BUSINESS_ERROR(3002, "业务处理失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
