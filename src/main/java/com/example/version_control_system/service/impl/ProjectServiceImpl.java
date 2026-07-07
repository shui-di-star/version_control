package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.dto.ProjectUpdateRequest;
import com.example.version_control_system.dto.ProjectVO;
import com.example.version_control_system.entity.Project;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.ProjectMapper;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    @Override
    public List<ProjectVO> listMyProjects(Long userId) {
        List<ProjectMember> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roleByProject = memberships.stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole, (a, b) -> a));
        List<Project> projects = projectMapper.selectByIds(roleByProject.keySet());
        return projects.stream()
                .map(p -> ProjectVO.from(p, roleByProject.get(p.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ProjectVO create(Long userId, ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerId(userId);
        projectMapper.insert(project);

        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(userId);
        member.setRole(ProjectRole.ADMIN.name());
        projectMemberMapper.insert(member);

        return ProjectVO.from(project, ProjectRole.ADMIN.name());
    }

    @Override
    @Transactional
    public ProjectVO update(Long projectId, ProjectUpdateRequest request) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }
        project.setName(request.name());
        project.setDescription(request.description());
        projectMapper.updateById(project);
        return ProjectVO.from(project, null);
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long currentUserId, boolean superAdmin) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }
        if (!superAdmin && !project.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅项目所有者可删除项目");
        }
        projectMapper.deleteById(projectId);
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
    }
}
