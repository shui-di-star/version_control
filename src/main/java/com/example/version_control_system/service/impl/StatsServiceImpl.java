package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.GlobalStatsVO;
import com.example.version_control_system.dto.ProjectStatsVO;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.StatsService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 统计实现：项目级（全体计数 + 分状态计数 + 指定 NUMBER 字段最大值 + 卡片/附件数）+ 全局汇总。 */
@Service
public class StatsServiceImpl implements StatsService {

    private final SimEntityMapper entityMapper;
    private final AssetMapper assetMapper;
    private final ProjectMemberMapper memberMapper;

    public StatsServiceImpl(SimEntityMapper entityMapper, AssetMapper assetMapper,
                            ProjectMemberMapper memberMapper) {
        this.entityMapper = entityMapper;
        this.assetMapper = assetMapper;
        this.memberMapper = memberMapper;
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
        long assetCount = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().inSql(Asset::getEntityId,
                        "SELECT id FROM t_entity WHERE project_id = " + projectId + " AND deleted = 0"));
        return new ProjectStatsVO(total, completedSim, simulating, recommended, maxValue,
                total, assetCount);
    }

    @Override
    public GlobalStatsVO globalStats(Long userId) {
        // 用户参与的项目列表
        List<ProjectMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId));
        long projectCount = memberships.size();
        if (projectCount == 0) {
            return new GlobalStatsVO(0, 0, 0);
        }
        List<Long> projectIds = memberships.stream().map(ProjectMember::getProjectId).toList();
        long cardCount = entityMapper.selectCount(
                new LambdaQueryWrapper<SimEntity>().in(SimEntity::getProjectId, projectIds));
        long assetCount = assetMapper.selectCount(
                new LambdaQueryWrapper<Asset>().inSql(Asset::getEntityId,
                        "SELECT id FROM t_entity WHERE project_id IN ("
                                + projectIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("")
                                + ") AND deleted = 0"));
        return new GlobalStatsVO(projectCount, cardCount, assetCount);
    }
}
