package com.example.version_control_system.dto;

/**
 * 设置实体状态标记请求。
 * <p>{@code status} 为 RECOMMENDED/DEPRECATED/SIMULATING/COMPLETED 之一（互斥单选），
 * null/空串表示清空状态。合法性由 Service 校验。</p>
 */
public record EntityStatusRequest(String status) {
}
