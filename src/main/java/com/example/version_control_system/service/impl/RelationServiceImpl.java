package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.JsonUtils;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.RelationCreateRequest;
import com.example.version_control_system.dto.RelationUpdateRequest;
import com.example.version_control_system.dto.RelationVO;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.RelationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 语义关系服务实现：端点归属校验 + allowed_from/allowed_to 类型约束。父子关系绝不入本表。 */
@Service
public class RelationServiceImpl implements RelationService {

    private final RelationMapper relationMapper;
    private final RelationTemplateMapper templateMapper;
    private final SimEntityMapper entityMapper;
    private final JsonUtils jsonUtils;

    public RelationServiceImpl(RelationMapper relationMapper,
                               RelationTemplateMapper templateMapper,
                               SimEntityMapper entityMapper,
                               JsonUtils jsonUtils) {
        this.relationMapper = relationMapper;
        this.templateMapper = templateMapper;
        this.entityMapper = entityMapper;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public List<RelationVO> list(Long projectId) {
        List<Relation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<Relation>().eq(Relation::getProjectId, projectId));
        return relations.stream().map(RelationVO::from).toList();
    }

    @Override
    public RelationVO get(Long projectId, Long relationId) {
        return RelationVO.from(requireRelation(projectId, relationId));
    }

    @Override
    @Transactional
    public RelationVO create(Long projectId, RelationCreateRequest request) {
        RelationTemplate template = requireTemplate(projectId, request.templateId());
        SimEntity from = requireEntity(projectId, request.fromEntityId());
        SimEntity to = requireEntity(projectId, request.toEntityId());
        validateAllowed(template.getAllowedFrom(), from.getTemplateId(), "源");
        validateAllowed(template.getAllowedTo(), to.getTemplateId(), "目标");

        Relation relation = new Relation();
        relation.setProjectId(projectId);
        relation.setTemplateId(request.templateId());
        relation.setFromEntityId(request.fromEntityId());
        relation.setToEntityId(request.toEntityId());
        relation.setRemark(request.remark());
        relationMapper.insert(relation);
        return RelationVO.from(relation);
    }

    @Override
    @Transactional
    public RelationVO update(Long projectId, Long relationId, RelationUpdateRequest request) {
        Relation relation = requireRelation(projectId, relationId);
        if (request.templateId() != null) {
            relation.setTemplateId(request.templateId());
        }
        relation.setRemark(request.remark());
        relationMapper.updateById(relation);
        return RelationVO.from(relation);
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long relationId) {
        requireRelation(projectId, relationId);
        relationMapper.deleteById(relationId);
    }

    private Relation requireRelation(Long projectId, Long relationId) {
        Relation relation = relationMapper.selectById(relationId);
        if (relation == null || !relation.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关系不存在");
        }
        return relation;
    }

    private RelationTemplate requireTemplate(Long projectId, Long templateId) {
        RelationTemplate template = templateMapper.selectById(templateId);
        if (template == null || !template.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关系模板不存在或不属于本项目");
        }
        return template;
    }

    private SimEntity requireEntity(Long projectId, Long entityId) {
        SimEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实体不存在或不属于本项目");
        }
        return entity;
    }

    /** allowed 为实体类型 id 数组 JSON；null/空=不限。非空时端点实体的模板 id 须在其中。 */
    private void validateAllowed(String allowedJson, Long entityTemplateId, String side) {
        JsonNode node = jsonUtils.parse(allowedJson);
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        Set<Long> allowed = new HashSet<>();
        for (JsonNode e : node) {
            if (e.isIntegralNumber()) {
                allowed.add(e.asLong());
            }
        }
        if (!allowed.contains(entityTemplateId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, side + "端实体类型不被该关系模板允许");
        }
    }
}
