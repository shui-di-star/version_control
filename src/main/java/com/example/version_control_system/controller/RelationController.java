package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.RelationCreateRequest;
import com.example.version_control_system.dto.RelationUpdateRequest;
import com.example.version_control_system.dto.RelationVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.RelationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 语义关系接口：读需成员，写需 Editor+。 */
@RestController
@RequestMapping("/api/projects/{projectId}/relations")
public class RelationController {

    private final RelationService relationService;

    public RelationController(RelationService relationService) {
        this.relationService = relationService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<RelationVO>> list(@PathVariable("projectId") Long projectId) {
        return Result.success(relationService.list(projectId));
    }

    @GetMapping("/{relationId}")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<RelationVO> get(@PathVariable("projectId") Long projectId,
                                  @PathVariable("relationId") Long relationId) {
        return Result.success(relationService.get(projectId, relationId));
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<RelationVO> create(@PathVariable("projectId") Long projectId,
                                     @Valid @RequestBody RelationCreateRequest request) {
        return Result.success(relationService.create(projectId, request));
    }

    @PutMapping("/{relationId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<RelationVO> update(@PathVariable("projectId") Long projectId,
                                     @PathVariable("relationId") Long relationId,
                                     @Valid @RequestBody RelationUpdateRequest request) {
        return Result.success(relationService.update(projectId, relationId, request));
    }

    @DeleteMapping("/{relationId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("relationId") Long relationId) {
        relationService.delete(projectId, relationId);
        return Result.success();
    }
}
