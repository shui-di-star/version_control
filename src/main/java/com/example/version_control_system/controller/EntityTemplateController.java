package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.EntityTemplateRequest;
import com.example.version_control_system.dto.EntityTemplateVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.EntityTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 实体模板接口：读需项目成员，写需项目 Admin。 */
@RestController
@RequestMapping("/api/projects/{projectId}/entity-templates")
public class EntityTemplateController {

    private final EntityTemplateService entityTemplateService;

    public EntityTemplateController(EntityTemplateService entityTemplateService) {
        this.entityTemplateService = entityTemplateService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<EntityTemplateVO>> list(@PathVariable("projectId") Long projectId) {
        return Result.success(entityTemplateService.list(projectId));
    }

    @GetMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<EntityTemplateVO> get(@PathVariable("projectId") Long projectId,
                                        @PathVariable("templateId") Long templateId) {
        return Result.success(entityTemplateService.get(projectId, templateId));
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<EntityTemplateVO> create(@PathVariable("projectId") Long projectId,
                                           @Valid @RequestBody EntityTemplateRequest request) {
        return Result.success(entityTemplateService.create(projectId, request));
    }

    @PutMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<EntityTemplateVO> update(@PathVariable("projectId") Long projectId,
                                           @PathVariable("templateId") Long templateId,
                                           @Valid @RequestBody EntityTemplateRequest request) {
        return Result.success(entityTemplateService.update(projectId, templateId, request));
    }

    @DeleteMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("templateId") Long templateId) {
        entityTemplateService.delete(projectId, templateId);
        return Result.success();
    }
}
