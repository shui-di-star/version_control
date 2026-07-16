package com.example.version_control_system.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 连线备注视图（含图片列表）。 */
public record EdgeRemarkVO(Long id, Long entityId, String content, Integer sortOrder,
                           List<EdgeRemarkImageVO> images, LocalDateTime createdAt) {
}
