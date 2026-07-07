package com.example.version_control_system.service;

import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.dto.ProjectUpdateRequest;
import com.example.version_control_system.dto.ProjectVO;

import java.util.List;

/** 项目服务：当前用户参与的项目列表与项目增删改。 */
public interface ProjectService {

    /** 当前用户参与的项目列表（含其在每个项目的角色）。 */
    List<ProjectVO> listMyProjects(Long userId);

    /** 创建项目，创建者自动成为该项目 ADMIN 成员。 */
    ProjectVO create(Long userId, ProjectCreateRequest request);

    /** 编辑项目（名称/描述）。项目须存在。 */
    ProjectVO update(Long projectId, ProjectUpdateRequest request);

    /** 删除项目：仅项目 owner（或 SuperAdmin）可删；连同其成员记录一并软删。 */
    void delete(Long projectId, Long currentUserId, boolean superAdmin);
}
