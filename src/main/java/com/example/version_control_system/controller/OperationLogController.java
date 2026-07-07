package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.OperationLogVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.OperationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 操作日志查询接口：仅项目 Admin 可查。 */
@RestController
@RequestMapping("/api/projects/{projectId}/logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public Result<List<OperationLogVO>> list(@PathVariable("projectId") Long projectId) {
        return Result.success(operationLogService.list(projectId));
    }
}
