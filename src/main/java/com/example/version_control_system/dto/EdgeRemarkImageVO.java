package com.example.version_control_system.dto;

import java.util.List;

/** 连线备注图片视图。 */
public record EdgeRemarkImageVO(Long id, String fileName, String objectKey, Long size, String mimeType) {
}
