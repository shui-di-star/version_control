package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.ProjectStatsVO;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.StatsService;
import org.springframework.stereotype.Service;

/** 统计实现：全体计数 + 分状态计数 + 指定 NUMBER 字段最大值。 */
@Service
public class StatsServiceImpl implements StatsService {

    private final SimEntityMapper entityMapper;

    public StatsServiceImpl(SimEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    @Override
    public ProjectStatsVO stats(Long projectId, String numberFieldKey) {
        long total = entityMapper.selectCount(
                new LambdaQueryWrapper<SimEntity>().eq(SimEntity::getProjectId, projectId));
        long recommended = entityMapper.countByStatus(projectId, "RECOMMENDED");
        long deprecated = entityMapper.countByStatus(projectId, "DEPRECATED");
        long completed = entityMapper.countByStatus(projectId, "COMPLETED");
        long simulating = entityMapper.countByStatus(projectId, "SIMULATING");
        long completedSim = recommended + deprecated + completed;
        Double maxValue = numberFieldKey == null || numberFieldKey.isBlank()
                ? null : entityMapper.maxNumberValue(projectId, numberFieldKey);
        return new ProjectStatsVO(total, completedSim, simulating, recommended, maxValue);
    }
}
