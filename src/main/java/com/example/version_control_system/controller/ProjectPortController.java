package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.ProjectExport;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.ProjectPortService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 项目导出 / 导入接口：仅项目 Admin 可用。 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectPortController {

    private final ProjectPortService projectPortService;

    public ProjectPortController(ProjectPortService projectPortService) {
        this.projectPortService = projectPortService;
    }

    @GetMapping("/export")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<ProjectExport> export(@PathVariable("projectId") Long projectId) {
        return Result.success(projectPortService.export(projectId));
    }

    @PostMapping("/import")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<Void> importInto(@PathVariable("projectId") Long projectId,
                                   @RequestBody ProjectExport data) {
        projectPortService.importInto(projectId, data);
        return Result.success();
    }
}
