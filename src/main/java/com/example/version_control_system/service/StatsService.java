package com.example.version_control_system.service;

import com.example.version_control_system.dto.GlobalStatsVO;
import com.example.version_control_system.dto.ProjectStatsVO;

/** 统计服务：项目级 + 全局汇总。 */
public interface StatsService {

    ProjectStatsVO stats(Long projectId, String numberFieldKey);

    /** 用户参与的所有项目的汇总统计（项目数、总卡片数、总附件数）。 */
    GlobalStatsVO globalStats(Long userId);
}
