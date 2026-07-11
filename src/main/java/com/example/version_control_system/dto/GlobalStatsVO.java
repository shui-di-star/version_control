package com.example.version_control_system.dto;

/**
 * 全局统计（跨项目汇总）：用户参与的项目数、总卡片数、总模型附件数。
 */
public record GlobalStatsVO(long projectCount, long cardCount, long assetCount) {
}
