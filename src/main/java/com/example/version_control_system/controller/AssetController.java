package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.AssetDownload;
import com.example.version_control_system.dto.AssetVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.AssetService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 产出物接口。挂在项目作用域下（{@code /api/projects/{projectId}/...}）以便 @RequireProjectRole 解析
 * projectId，与 EntityController 的既定路径偏离一致（设计文档写 /api/entities/{id}/assets）。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/entities/{entityId}/assets")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<AssetVO> upload(@PathVariable("projectId") Long projectId,
                                  @PathVariable("entityId") Long entityId,
                                  @RequestParam("assetType") String assetType,
                                  @RequestParam("file") MultipartFile file) {
        return Result.success(assetService.upload(projectId, entityId, assetType, file));
    }

    @PostMapping("/entities/{entityId}/assets/text")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<AssetVO> uploadText(@PathVariable("projectId") Long projectId,
                                      @PathVariable("entityId") Long entityId,
                                      @RequestParam("fileName") String fileName,
                                      @RequestParam("contentText") String contentText) {
        return Result.success(assetService.uploadText(projectId, entityId, fileName, contentText));
    }

    @GetMapping("/entities/{entityId}/assets")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<AssetVO>> list(@PathVariable("projectId") Long projectId,
                                      @PathVariable("entityId") Long entityId) {
        return Result.success(assetService.list(projectId, entityId));
    }

    @GetMapping("/assets/{assetId}/download")
    @RequireProjectRole(ProjectRole.VIEWER)
    public ResponseEntity<InputStreamResource> download(@PathVariable("projectId") Long projectId,
                                                        @PathVariable("assetId") Long assetId) {
        AssetDownload d = assetService.download(projectId, assetId);
        String encoded = URLEncoder.encode(d.fileName() == null ? "download" : d.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType mediaType = d.mimeType() != null
                ? MediaType.parseMediaType(d.mimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType);
        if (d.size() != null) {
            builder.contentLength(d.size());
        }
        return builder.body(new InputStreamResource(d.stream()));
    }

    /** 图片内联预览：返回二进制流 + Content-Type，供前端 &lt;img src="..."&gt; 直接引用。公开端点。 */
    @GetMapping("/assets/{assetId}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable("projectId") Long projectId,
                                                       @PathVariable("assetId") Long assetId) {
        AssetDownload d = assetService.download(projectId, assetId);
        MediaType mediaType = d.mimeType() != null
                ? MediaType.parseMediaType(d.mimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(mediaType);
        if (d.size() != null) {
            builder.contentLength(d.size());
        }
        return builder.body(new InputStreamResource(d.stream()));
    }

    @DeleteMapping("/assets/{assetId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("assetId") Long assetId) {
        assetService.delete(projectId, assetId);
        return Result.success();
    }
}
