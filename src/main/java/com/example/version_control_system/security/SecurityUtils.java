package com.example.version_control_system.security;

import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 Spring Security 上下文取当前认证用户的工具。 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 返回当前 {@link AuthUser}；未认证时抛 UNAUTHORIZED 业务异常。 */
    public static AuthUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return authUser;
    }
}
