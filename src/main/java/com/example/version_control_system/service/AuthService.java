package com.example.version_control_system.service;

import com.example.version_control_system.dto.LoginRequest;
import com.example.version_control_system.dto.LoginResponse;
import com.example.version_control_system.dto.RegisterRequest;
import com.example.version_control_system.dto.UserInfo;

/** 认证服务：注册、登录、当前用户、登出。 */
public interface AuthService {

    /** 注册新用户，返回其信息。username 重复抛冲突异常。 */
    UserInfo register(RegisterRequest request);

    /** 校验凭据并签发 JWT。 */
    LoginResponse login(LoginRequest request);

    /** 返回指定用户信息。 */
    UserInfo getUserInfo(Long userId);

    /** 登出：将 token_invalid_before 置为当前时间，使该用户已签发的全部 token 失效（决策 6）。 */
    void logout(Long userId);
}
