package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.JsonUtils;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.RelationTemplateRequest;
import com.example.version_control_system.dto.RelationTemplateVO;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.service.RelationTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class RelationTemplateServiceImpl implements RelationTemplateService {

    private final RelationTemplateMapper templateMapper;
    private final RelationMapper relationMapper;
    private final JsonUtils jsonUtils;

    public RelationTemplateServiceImpl(RelationTemplateMapper templateMapper,
                                       RelationMapper relationMapper,
                                       JsonUtils jsonUtils) {
        this.templateMapper = templateMapper;
        this.relationMapper = relationMapper;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public List<RelationTemplateVO> list(Long projectId) {
        List<RelationTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<RelationTemplate>().eq(RelationTemplate::getProjectId, projectId));
        return templates.stream().map(RelationTemplateVO::from).toList();
    }

    @Override
    public RelationTemplateVO get(Long projectId, Long templateId) {
        return RelationTemplateVO.from(require(projectId, templateId));
    }

    @Override
    @Transactional
    public RelationTemplateVO create(Long projectId, RelationTemplateRequest request) {
        RelationTemplate template = new RelationTemplate();
        template.setProjectId(projectId);
        template.setName(request.name());
        template.setDirected(request.directed() != null && request.directed() != 0 ? 1 : 0);
        template.setLineStyle(validateObject(request.lineStyle(), "lineStyle"));
        template.setAllowedFrom(validateIdArray(request.allowedFrom(), "allowedFrom"));
        template.setAllowedTo(validateIdArray(request.allowedTo(), "allowedTo"));
        templateMapper.insert(template);
        return RelationTemplateVO.from(template);
    }

    @Override
    @Transactional
    public RelationTemplateVO update(Long projectId, Long templateId, RelationTemplateRequest request) {
        RelationTemplate template = require(projectId, templateId);
        template.setName(request.name());
        template.setDirected(request.directed() != null && request.directed() != 0 ? 1 : 0);
        template.setLineStyle(validateObject(request.lineStyle(), "lineStyle"));
        template.setAllowedFrom(validateIdArray(request.allowedFrom(), "allowedFrom"));
        template.setAllowedTo(validateIdArray(request.allowedTo(), "allowedTo"));
        templateMapper.updateById(template);
        return RelationTemplateVO.from(template);
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long templateId) {
        require(projectId, templateId);
        long referencing = relationMapper.selectCount(new LambdaQueryWrapper<Relation>()
                .eq(Relation::getTemplateId, templateId));
        if (referencing > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "模板已被关系引用，无法删除");
        }
        templateMapper.deleteById(templateId);
    }

    private RelationTemplate require(Long projectId, Long templateId) {
        RelationTemplate template = templateMapper.selectById(templateId);
        if (template == null || !template.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关系模板不存在");
        }
        return template;
    }

    /** 校验为合法 JSON 对象（null/空放行），返回原始串。 */
    private String validateObject(String json, String field) {
        JsonNode node = jsonUtils.parse(json);
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, field + " 必须是 JSON 对象");
        }
        return json;
    }

    /** 校验为合法 id 数组（null/空放行），返回原始串。 */
    private String validateIdArray(String json, String field) {
        JsonNode node = jsonUtils.parse(json);
        if (node == null) {
            return null;
        }
        if (!node.isArray()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, field + " 必须是数组");
        }
        for (JsonNode e : node) {
            if (!e.isIntegralNumber()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, field + " 元素必须是实体类型 id");
            }
        }
        return json;
    }
}
