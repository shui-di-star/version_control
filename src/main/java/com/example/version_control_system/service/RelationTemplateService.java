package com.example.version_control_system.service;

import com.example.version_control_system.dto.RelationTemplateRequest;
import com.example.version_control_system.dto.RelationTemplateVO;

import java.util.List;

/** 关系模板服务：项目内关系模板增删改查。 */
public interface RelationTemplateService {

    List<RelationTemplateVO> list(Long projectId);

    RelationTemplateVO get(Long projectId, Long templateId);

    RelationTemplateVO create(Long projectId, RelationTemplateRequest request);

    RelationTemplateVO update(Long projectId, Long templateId, RelationTemplateRequest request);

    /** 删除模板；若已被 t_relation 引用则拒绝。 */
    void delete(Long projectId, Long templateId);
}
