package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.ChildStrategy;
import com.example.version_control_system.dto.EntityCreateRequest;
import com.example.version_control_system.dto.EntityReparentRequest;
import com.example.version_control_system.dto.EntityStatusRequest;
import com.example.version_control_system.dto.EntityTreeNode;
import com.example.version_control_system.dto.EntityUpdateRequest;
import com.example.version_control_system.dto.EntityVO;
import com.example.version_control_system.log.LogOperation;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 项目内实体接口：读需成员，写需 Editor+。 */
@RestController
@RequestMapping("/api/projects/{projectId}/entities")
public class EntityController {

    private final EntityService entityService;

    public EntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping("/tree")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<EntityTreeNode>> tree(@PathVariable("projectId") Long projectId) {
        return Result.success(entityService.tree(projectId));
    }

    @GetMapping("/{entityId}")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<EntityVO> get(@PathVariable("projectId") Long projectId,
                                @PathVariable("entityId") Long entityId) {
        return Result.success(entityService.get(projectId, entityId));
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.EDITOR)
    @LogOperation(action = "CREATE_ENTITY", targetType = "ENTITY")
    public Result<EntityVO> create(@PathVariable("projectId") Long projectId,
                                   @Valid @RequestBody EntityCreateRequest request) {
        return Result.success(entityService.create(projectId, request));
    }

    @PutMapping("/{entityId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EntityVO> update(@PathVariable("projectId") Long projectId,
                                   @PathVariable("entityId") Long entityId,
                                   @Valid @RequestBody EntityUpdateRequest request) {
        return Result.success(entityService.update(projectId, entityId, request));
    }

    @DeleteMapping("/{entityId}")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<Void> delete(@PathVariable("projectId") Long projectId,
                               @PathVariable("entityId") Long entityId,
                               @RequestParam(value = "childStrategy", defaultValue = "CASCADE") ChildStrategy childStrategy) {
        entityService.delete(projectId, entityId, childStrategy);
        return Result.success();
    }

    @GetMapping("/{entityId}/path")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<EntityVO>> path(@PathVariable("projectId") Long projectId,
                                       @PathVariable("entityId") Long entityId) {
        return Result.success(entityService.path(projectId, entityId));
    }

    @PutMapping("/{entityId}/milestone")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EntityVO> toggleMilestone(@PathVariable("projectId") Long projectId,
                                            @PathVariable("entityId") Long entityId) {
        return Result.success(entityService.toggleMilestone(projectId, entityId));
    }

    @PutMapping("/{entityId}/status")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EntityVO> setStatus(@PathVariable("projectId") Long projectId,
                                      @PathVariable("entityId") Long entityId,
                                      @RequestBody EntityStatusRequest request) {
        return Result.success(entityService.setStatus(projectId, entityId, request.status()));
    }

    @PutMapping("/{entityId}/parent")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<EntityVO> reparent(@PathVariable("projectId") Long projectId,
                                     @PathVariable("entityId") Long entityId,
                                     @Valid @RequestBody EntityReparentRequest request) {
        return Result.success(entityService.reparent(projectId, entityId, request));
    }
}
