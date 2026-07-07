package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.FieldSchema;
import com.example.version_control_system.common.JsonUtils;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.EntityTemplateRequest;
import com.example.version_control_system.dto.EntityTemplateVO;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.EntityTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.service.EntityTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EntityTemplateServiceImpl implements EntityTemplateService {

    private final EntityTemplateMapper templateMapper;
    private final SimEntityMapper entityMapper;
    private final JsonUtils jsonUtils;

    public EntityTemplateServiceImpl(EntityTemplateMapper templateMapper,
                                     SimEntityMapper entityMapper,
                                     JsonUtils jsonUtils) {
        this.templateMapper = templateMapper;
        this.entityMapper = entityMapper;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public List<EntityTemplateVO> list(Long projectId) {
        List<EntityTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<EntityTemplate>().eq(EntityTemplate::getProjectId, projectId));
        return templates.stream().map(EntityTemplateVO::from).toList();
    }

    @Override
    public EntityTemplateVO get(Long projectId, Long templateId) {
        return EntityTemplateVO.from(require(projectId, templateId));
    }

    @Override
    @Transactional
    public EntityTemplateVO create(Long projectId, EntityTemplateRequest request) {
        String normalized = normalizeSchema(request.fieldSchema());
        EntityTemplate template = new EntityTemplate();
        template.setProjectId(projectId);
        template.setName(request.name());
        template.setIcon(request.icon());
        template.setFieldSchema(normalized);
        templateMapper.insert(template);
        return EntityTemplateVO.from(template);
    }

    @Override
    @Transactional
    public EntityTemplateVO update(Long projectId, Long templateId, EntityTemplateRequest request) {
        EntityTemplate template = require(projectId, templateId);
        String normalized = normalizeSchema(request.fieldSchema());
        template.setName(request.name());
        template.setIcon(request.icon());
        template.setFieldSchema(normalized);
        templateMapper.updateById(template);
        return EntityTemplateVO.from(template);
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long templateId) {
        require(projectId, templateId);
        long referencing = entityMapper.selectCount(new LambdaQueryWrapper<SimEntity>()
                .eq(SimEntity::getTemplateId, templateId));
        if (referencing > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "模板已被实体引用，无法删除");
        }
        templateMapper.deleteById(templateId);
    }

    /** 查模板并校验归属项目，不存在或跨项目均抛 NOT_FOUND。 */
    private EntityTemplate require(Long projectId, Long templateId) {
        EntityTemplate template = templateMapper.selectById(templateId);
        if (template == null || !template.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实体模板不存在");
        }
        return template;
    }

    /** 校验 field_schema 合法后返回原始串（null/空返回 null）。 */
    private String normalizeSchema(String fieldSchema) {
        FieldSchema.parse(jsonUtils.parse(fieldSchema));
        return fieldSchema == null || fieldSchema.isBlank() ? null : fieldSchema;
    }
}
