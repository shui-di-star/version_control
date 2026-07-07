package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.dto.SearchHit;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 搜索实现：对 t_entity.name/remark/parent_relation_remark、t_relation.remark 做 LIKE 匹配。 */
@Service
public class SearchServiceImpl implements SearchService {

    private final SimEntityMapper entityMapper;
    private final RelationMapper relationMapper;

    public SearchServiceImpl(SimEntityMapper entityMapper, RelationMapper relationMapper) {
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
    }

    @Override
    public List<SearchHit> search(Long projectId, String keyword) {
        List<SearchHit> hits = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return hits;
        }

        // 实体：名称、备注、父子关系备注
        List<SimEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<SimEntity>()
                .eq(SimEntity::getProjectId, projectId)
                .and(w -> w.like(SimEntity::getName, keyword)
                        .or().like(SimEntity::getRemark, keyword)
                        .or().like(SimEntity::getParentRelationRemark, keyword)));
        for (SimEntity e : entities) {
            if (e.getName() != null && e.getName().contains(keyword)) {
                hits.add(new SearchHit("ENTITY", e.getId(), null, null, null, "name", e.getName()));
            }
            if (e.getRemark() != null && e.getRemark().contains(keyword)) {
                hits.add(new SearchHit("ENTITY", e.getId(), null, null, null, "remark", e.getRemark()));
            }
            if (e.getParentRelationRemark() != null && e.getParentRelationRemark().contains(keyword)) {
                // 父子关系备注命中：fromEntityId = parentId, toEntityId = 自身
                hits.add(new SearchHit("PARENT_RELATION", e.getId(), null,
                        e.getParentId(), e.getId(), "关系备注", e.getParentRelationRemark()));
            }
        }

        // 语义关系备注
        List<Relation> relations = relationMapper.selectList(new LambdaQueryWrapper<Relation>()
                .eq(Relation::getProjectId, projectId)
                .like(Relation::getRemark, keyword));
        for (Relation r : relations) {
            hits.add(new SearchHit("RELATION", null, r.getId(),
                    r.getFromEntityId(), r.getToEntityId(), "remark", r.getRemark()));
        }
        return hits;
    }
}
