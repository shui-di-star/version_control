package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.RelationTemplateRequest;
import com.example.version_control_system.dto.RelationTemplateVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.RelationTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 关系模板接口：读需项目成员，写需项目 Admin。 */
@RestController
@RequestMapping("/api/projects/{projectId}/relation-templates")
public class RelationTemplateController {

    private final RelationTemplateService relationTemplateService;

    public RelationTemplateController(RelationTemplateService relationTemplateService) {
        this.relationTemplateService = relationTemplateService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<RelationTemplateVO>> list(@PathVariable("projectId") Long projectId) {
        return Result.success(relationTemplateService.list(projectId));
    }

    @GetMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<RelationTemplateVO> get(@PathVariable("projectId") Long projectId,
                                          @PathVariable("templateId") Long templateId) {
        return Result.success(relationTemplateService.get(projectId, templateId));
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<RelationTemplateVO> create(@PathVariable("projectId") Long projectId,
                                             @Valid @RequestBody RelationTemplateRequest request) {
        return Result.success(relationTemplateService.create(projectId, request));
    }

    @PutMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<RelationTemplateVO> update(@PathVariable("projectId") Long projectId,
                                             @PathVariable("templateId") Long templateId,
                                             @Valid @RequestBody RelationTemplateRequest request) {
        return Result.success(relationTemplateService.update(projectId, templateId, request));
    }

    @DeleteMapping("/{templateId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("templateId") Long templateId) {
        relationTemplateService.delete(projectId, templateId);
        return Result.success();
    }
}
