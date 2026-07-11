package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.AttrImageRefService;
import com.example.version_control_system.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

/**
 * 属性图片接口：与产出物（Asset）完全独立。
 * <p>上传后存入 MinIO 的 {@code p{projectId}/attr/} 路径下，
 * 返回 objectKey 字符串，前端将其存入实体 attributes JSON。</p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/attr-images")
public class AttrImageController {

    private final StorageService storageService;
    private final AttrImageRefService attrImageRefService;

    public AttrImageController(StorageService storageService, AttrImageRefService attrImageRefService) {
        this.storageService = storageService;
        this.attrImageRefService = attrImageRefService;
    }

    /** 上传属性图片，返回 objectKey。 */
    @PostMapping
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Map<String, String>> upload(@PathVariable("projectId") Long projectId,
                                               @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持图片文件");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
        String objectKey = "p" + projectId + "/attr/" + UUID.randomUUID() + "-" + originalName;
        try {
            storageService.upload(objectKey, file.getInputStream(), file.getSize(), contentType);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "属性图片上传失败：" + e.getMessage());
        }
        attrImageRefService.addRef(projectId, objectKey);
        return Result.success(Map.of("objectKey", objectKey));
    }

    /** 预览属性图片（公开端点，供 img src 使用）。 */
    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable("projectId") Long projectId,
                                                        @RequestParam("key") String objectKey) {
        // 安全校验：只允许访问本项目路径下的属性图片
        if (!objectKey.startsWith("p" + projectId + "/attr/")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该图片");
        }
        InputStream stream = storageService.download(objectKey);
        // 从 objectKey 推断 content type
        String ct = guessContentType(objectKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(MediaType.parseMediaType(ct))
                .body(new InputStreamResource(stream));
    }

    private String guessContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
