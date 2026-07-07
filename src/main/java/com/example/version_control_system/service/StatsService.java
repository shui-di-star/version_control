package com.example.version_control_system.service;

import com.example.version_control_system.dto.ProjectStatsVO;

/** 项目统计服务。 */
public interface StatsService {

    ProjectStatsVO stats(Long projectId, String numberFieldKey);
}
