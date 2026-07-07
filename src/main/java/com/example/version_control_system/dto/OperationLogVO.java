package com.example.version_control_system.dto;

import com.example.version_control_system.entity.OperationLog;

import java.time.LocalDateTime;

/** 操作日志视图。 */
public record OperationLogVO(Long id, Long projectId, Long userId, String action,
                             String targetType, Long targetId, String detail,
                             LocalDateTime createdAt) {

    public static OperationLogVO from(OperationLog l) {
        return new OperationLogVO(l.getId(), l.getProjectId(), l.getUserId(), l.getAction(),
                l.getTargetType(), l.getTargetId(), l.getDetail(), l.getCreatedAt());
    }
}
