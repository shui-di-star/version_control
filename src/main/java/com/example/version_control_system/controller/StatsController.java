package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.GlobalStatsVO;
import com.example.version_control_system.dto.ProjectStatsVO;
import com.example.version_control_system.security.AuthUser;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.security.SecurityUtils;
import com.example.version_control_system.service.StatsService;
import org.springframework.web.bind.annotation.*;

/** 统计接口：项目级（成员可读）+ 全局（登录即可）。 */
@RestController
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/api/projects/{projectId}/stats")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<ProjectStatsVO> stats(@PathVariable("projectId") Long projectId,
                                        @RequestParam(value = "numberFieldKey", required = false) String numberFieldKey) {
        return Result.success(statsService.stats(projectId, numberFieldKey));
    }

    /** 全局统计：当前用户参与的所有项目的汇总（项目数、卡片数、附件数）。 */
    @GetMapping("/api/stats/global")
    public Result<GlobalStatsVO> globalStats() {
        AuthUser user = SecurityUtils.getCurrentUser();
        return Result.success(statsService.globalStats(user.userId()));
    }
}
