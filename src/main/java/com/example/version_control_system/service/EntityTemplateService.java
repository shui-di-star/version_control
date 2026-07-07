package com.example.version_control_system.service;

import com.example.version_control_system.dto.EntityTemplateRequest;
import com.example.version_control_system.dto.EntityTemplateVO;

import java.util.List;

/** 实体模板服务：项目内实体模板增删改查。 */
public interface EntityTemplateService {

    List<EntityTemplateVO> list(Long projectId);

    EntityTemplateVO get(Long projectId, Long templateId);

    EntityTemplateVO create(Long projectId, EntityTemplateRequest request);

    EntityTemplateVO update(Long projectId, Long templateId, EntityTemplateRequest request);

    /** 删除模板；若已被 t_entity 引用则拒绝。 */
    void delete(Long projectId, Long templateId);
}
