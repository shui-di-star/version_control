package com.example.version_control_system.exception;

import com.example.version_control_system.common.ResultCode;

/**
 * 业务异常。抛出后由全局异常处理器转为统一 {@code Result} 错误响应。
 */
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
