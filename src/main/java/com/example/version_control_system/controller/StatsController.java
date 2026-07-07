package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.ProjectStatsVO;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.StatsService;
import org.springframework.web.bind.annotation.*;

/** 项目统计接口：成员可读。 */
@RestController
@RequestMapping("/api/projects/{projectId}/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<ProjectStatsVO> stats(@PathVariable("projectId") Long projectId,
                                        @RequestParam(value = "numberFieldKey", required = false) String numberFieldKey) {
        return Result.success(statsService.stats(projectId, numberFieldKey));
    }
}
