package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.ProjectCreateRequest;
import com.example.version_control_system.dto.ProjectUpdateRequest;
import com.example.version_control_system.dto.ProjectVO;
import com.example.version_control_system.entity.Asset;
import com.example.version_control_system.entity.EntityTemplate;
import com.example.version_control_system.entity.Project;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.Relation;
import com.example.version_control_system.entity.RelationTemplate;
import com.example.version_control_system.entity.SimEntity;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.mapper.AssetMapper;
import com.example.version_control_system.mapper.EntityTemplateMapper;
import com.example.version_control_system.mapper.ProjectMapper;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.RelationMapper;
import com.example.version_control_system.mapper.RelationTemplateMapper;
import com.example.version_control_system.mapper.SimEntityMapper;
import com.example.version_control_system.mapper.UserMapper;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.service.ProjectService;
import com.example.version_control_system.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final RelationTemplateMapper relationTemplateMapper;
    private final EntityTemplateMapper entityTemplateMapper;
    private final SimEntityMapper entityMapper;
    private final RelationMapper relationMapper;
    private final AssetMapper assetMapper;
    private final StorageService storageService;
    private final UserMapper userMapper;

    /** 新项目自动创建的预设关系模板。 */
    private static final List<Map<String, String>> PRESET_RELATIONS = List.of(
            Map.of("name", "结构迭代", "color", "#1890ff"),
            Map.of("name", "材料替换", "color", "#52c41a"),
            Map.of("name", "减重优化", "color", "#faad14"),
            Map.of("name", "参数调整", "color", "#722ed1")
    );

    public ProjectServiceImpl(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper,
                               RelationTemplateMapper relationTemplateMapper,
                               EntityTemplateMapper entityTemplateMapper,
                               SimEntityMapper entityMapper,
                               RelationMapper relationMapper,
                               AssetMapper assetMapper,
                               StorageService storageService,
                               UserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.relationTemplateMapper = relationTemplateMapper;
        this.entityTemplateMapper = entityTemplateMapper;
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
        this.assetMapper = assetMapper;
        this.storageService = storageService;
        this.userMapper = userMapper;
    }

    @Override
    public List<ProjectVO> listMyProjects(Long userId, boolean superAdmin) {
        if (superAdmin) {
            List<Project> allProjects = projectMapper.selectList(new LambdaQueryWrapper<>());
            Map<Long, String> ownerNames = resolveOwnerNames(allProjects);
            return allProjects.stream()
                    .map(p -> ProjectVO.from(p, ProjectRole.ADMIN.name(), ownerNames.get(p.getOwnerId())))
                    .toList();
        }
        List<ProjectMember> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roleByProject = memberships.stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole, (a, b) -> a));
        List<Project> projects = projectMapper.selectByIds(roleByProject.keySet());
        Map<Long, String> ownerNames = resolveOwnerNames(projects);
        return projects.stream()
                .map(p -> ProjectVO.from(p, roleByProject.get(p.getId()), ownerNames.get(p.getOwnerId())))
                .toList();
    }

    private Map<Long, String> resolveOwnerNames(List<Project> projects) {
        var ownerIds = projects.stream().map(Project::getOwnerId).distinct().toList();
        if (ownerIds.isEmpty()) return Map.of();
        List<User> owners = userMapper.selectByIds(ownerIds);
        return owners.stream().collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
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

        // 自动创建预设关系模板
        for (Map<String, String> preset : PRESET_RELATIONS) {
            RelationTemplate rt = new RelationTemplate();
            rt.setProjectId(project.getId());
            rt.setName(preset.get("name"));
            rt.setDirected(1);
            rt.setLineStyle("{\"color\":\"" + preset.get("color") + "\",\"dash\":false,\"width\":2}");
            relationTemplateMapper.insert(rt);
        }

        User owner = userMapper.selectById(userId);
        String ownerName = owner != null ? (owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername()) : null;
        return ProjectVO.from(project, ProjectRole.ADMIN.name(), ownerName);
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
        User owner = userMapper.selectById(project.getOwnerId());
        String ownerName = owner != null ? (owner.getDisplayName() != null ? owner.getDisplayName() : owner.getUsername()) : null;
        return ProjectVO.from(project, null, ownerName);
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

        // 清理 MinIO 中该项目所有产出物文件
        List<Asset> assets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .inSql(Asset::getEntityId,
                        "SELECT id FROM t_entity WHERE project_id = " + projectId + " AND deleted = 0"));
        for (Asset asset : assets) {
            if (asset.getObjectKey() != null && !asset.getObjectKey().isBlank()) {
                try {
                    storageService.delete(asset.getObjectKey());
                } catch (Exception ignored) {
                    // MinIO 删除失败不阻断流程
                }
            }
        }

        // 清理数据库：产出物、关系、实体、模板、成员
        assetMapper.delete(new LambdaQueryWrapper<Asset>()
                .inSql(Asset::getEntityId,
                        "SELECT id FROM t_entity WHERE project_id = " + projectId + " AND deleted = 0"));
        relationMapper.delete(new LambdaQueryWrapper<Relation>()
                .inSql(Relation::getFromEntityId,
                        "SELECT id FROM t_entity WHERE project_id = " + projectId + " AND deleted = 0"));
        entityMapper.delete(new LambdaQueryWrapper<SimEntity>()
                .eq(SimEntity::getProjectId, projectId));
        entityTemplateMapper.delete(new LambdaQueryWrapper<EntityTemplate>()
                .eq(EntityTemplate::getProjectId, projectId));
        relationTemplateMapper.delete(new LambdaQueryWrapper<RelationTemplate>()
                .eq(RelationTemplate::getProjectId, projectId));
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
        projectMapper.deleteById(projectId);
    }
}
