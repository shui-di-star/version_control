package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.dto.ProjectUpdateRequest;
import com.example.version_control_system.dto.ProjectVO;
import com.example.version_control_system.security.AuthUser;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.security.SecurityUtils;
import com.example.version_control_system.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 项目接口：列表/创建对登录用户开放；编辑需项目 Admin；删除仅 owner。 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<ProjectVO>> myProjects() {
        AuthUser current = SecurityUtils.getCurrentUser();
        return Result.success(projectService.listMyProjects(current.userId(), current.isSuperAdmin()));
    }

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectCreateRequest request) {
        AuthUser current = SecurityUtils.getCurrentUser();
        return Result.success(projectService.create(current.userId(), request));
    }

    @PutMapping("/{projectId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<ProjectVO> update(@PathVariable("projectId") Long projectId,
                                    @Valid @RequestBody ProjectUpdateRequest request) {
        return Result.success(projectService.update(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> delete(@PathVariable("projectId") Long projectId) {
        AuthUser current = SecurityUtils.getCurrentUser();
        projectService.delete(projectId, current.userId(), current.isSuperAdmin());
        return Result.success();
    }
}
