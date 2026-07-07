package com.example.version_control_system.dto;

/**
 * 项目统计面板数据。
 * <p>{@code totalNodes} 全部实体（方案节点，决策 4）；{@code completedSim} 已完成仿真
 * = RECOMMENDED + DEPRECATED + COMPLETED（决策 3）；{@code simulating} 仿真中(SIMULATING)；
 * {@code recommended} 推荐方案(RECOMMENDED)；{@code maxNumberValue} 指定 NUMBER 字段最大值
 * （无 numberFieldKey 或无数据时为 null）。</p>
 */
public record ProjectStatsVO(long totalNodes, long completedSim, long simulating,
                             long recommended, Double maxNumberValue) {
}
