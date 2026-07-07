package com.example.version_control_system.exception;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：将异常统一转换为 {@link Result} 错误响应，避免向客户端暴露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：使用其自带的错误码与消息。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getResultCode().getCode(), ex.getMessage());
        return Result.error(ex.getResultCode(), ex.getMessage());
    }

    /**
     * 项目级授权失败（{@code @RequireProjectRole} 切面抛出）。
     * <p>切面在 DispatcherServlet 内抛出，先于 Security 的 ExceptionTranslationFilter 被此处捕获，
     * 故在这里显式映射为 HTTP 403 + FORBIDDEN 错误体。</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("访问被拒: {}", ex.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }

    /** Bean Validation 校验失败（@Valid 请求体）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + defaultMessage(fe))
                .collect(Collectors.joining("; "));
        return Result.error(ResultCode.VALIDATION_ERROR, detail);
    }

    /** 兜底：未预期的异常统一转为 500，不泄露堆栈给客户端。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return Result.error(ResultCode.INTERNAL_ERROR);
    }

    private static String defaultMessage(FieldError fe) {
        return fe.getDefaultMessage() == null ? "非法值" : fe.getDefaultMessage();
    }
}
