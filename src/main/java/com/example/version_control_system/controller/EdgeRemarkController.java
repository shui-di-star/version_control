package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.EdgeRemarkCreateRequest;
import com.example.version_control_system.dto.EdgeRemarkImageVO;
import com.example.version_control_system.dto.EdgeRemarkUpdateRequest;
import com.example.version_control_system.dto.EdgeRemarkVO;
import com.example.version_control_system.entity.EdgeRemarkImage;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.EdgeRemarkService;
import com.example.version_control_system.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/** 连线备注接口。 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class EdgeRemarkController {

    private final EdgeRemarkService edgeRemarkService;
    private final StorageService storageService;

    public EdgeRemarkController(EdgeRemarkService edgeRemarkService, StorageService storageService) {
        this.edgeRemarkService = edgeRemarkService;
        this.storageService = storageService;
    }

    @GetMapping("/entities/{entityId}/edge-remarks")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<EdgeRemarkVO>> list(@PathVariable("projectId") Long projectId,
                                           @PathVariable("entityId") Long entityId) {
        return Result.success(edgeRemarkService.list(projectId, entityId));
    }

    @PostMapping("/entities/{entityId}/edge-remarks")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EdgeRemarkVO> create(@PathVariable("projectId") Long projectId,
                                       @PathVariable("entityId") Long entityId,
                                       @Valid @RequestBody EdgeRemarkCreateRequest req) {
        return Result.success(edgeRemarkService.create(projectId, entityId, req.content()));
    }

    @PutMapping("/edge-remarks/{remarkId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EdgeRemarkVO> update(@PathVariable("projectId") Long projectId,
                                       @PathVariable("remarkId") Long remarkId,
                                       @Valid @RequestBody EdgeRemarkUpdateRequest req) {
        return Result.success(edgeRemarkService.update(projectId, remarkId, req.content()));
    }

    @DeleteMapping("/edge-remarks/{remarkId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("remarkId") Long remarkId) {
        edgeRemarkService.delete(projectId, remarkId);
        return Result.success();
    }

    @PostMapping("/edge-remarks/{remarkId}/images")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EdgeRemarkImageVO> uploadImage(@PathVariable("projectId") Long projectId,
                                                  @PathVariable("remarkId") Long remarkId,
                                                  @RequestParam("file") MultipartFile file) {
        return Result.success(edgeRemarkService.uploadImage(projectId, remarkId, file));
    }

    @DeleteMapping("/edge-remark-images/{imageId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Void> deleteImage(@PathVariable("projectId") Long projectId,
                                    @PathVariable("imageId") Long imageId) {
        edgeRemarkService.deleteImage(projectId, imageId);
        return Result.success();
    }

    /** 备注图片预览：返回二进制流，供前端 img 标签引用。 */
    @GetMapping("/edge-remark-images/{imageId}/preview")
    public ResponseEntity<InputStreamResource> previewImage(@PathVariable("projectId") Long projectId,
                                                             @PathVariable("imageId") Long imageId) {
        EdgeRemarkImage img = edgeRemarkService.getImage(projectId, imageId);
        InputStream stream = storageService.download(img.getObjectKey());
        MediaType mediaType = img.getMimeType() != null
                ? MediaType.parseMediaType(img.getMimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(mediaType);
        if (img.getSize() != null) {
            builder.contentLength(img.getSize());
        }
        return builder.body(new InputStreamResource(stream));
    }
}
