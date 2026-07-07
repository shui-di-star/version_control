package com.example.version_control_system.testsupport;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅测试用的控制器：暴露带 {@link RequireProjectRole} 的端点，供 ProjectRoleAspect 集成测试驱动。
 */
@RestController
public class ProjectRoleTestController {

    @GetMapping("/test/projects/{pid}/viewer-op")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<String> viewerOp(@PathVariable("pid") Long pid) {
        return Result.success("ok");
    }

    @GetMapping("/test/projects/{pid}/editor-op")
    @RequireProjectRole(ProjectRole.EDITOR)
    public Result<String> editorOp(@PathVariable("pid") Long pid) {
        return Result.success("ok");
    }

    @GetMapping("/test/projects/{pid}/admin-op")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<String> adminOp(@PathVariable("pid") Long pid) {
        return Result.success("ok");
    }
}
