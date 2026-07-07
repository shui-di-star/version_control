package com.example.version_control_system.dto;

import java.io.InputStream;

/** 产出物下载载体：流 + 文件名 + MIME + 大小。 */
public record AssetDownload(InputStream stream, String fileName, String mimeType, Long size) {
}
