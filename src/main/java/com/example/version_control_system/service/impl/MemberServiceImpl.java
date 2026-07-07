package com.example.version_control_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.dto.MemberAddRequest;
import com.example.version_control_system.dto.MemberVO;
import com.example.version_control_system.entity.Project;
import com.example.version_control_system.entity.ProjectMember;
import com.example.version_control_system.entity.User;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.mapper.ProjectMapper;
import com.example.version_control_system.mapper.ProjectMemberMapper;
import com.example.version_control_system.mapper.UserMapper;
import com.example.version_control_system.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MemberServiceImpl implements MemberService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;

    public MemberServiceImpl(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper, UserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<MemberVO> list(Long projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).toList();
        Map<Long, User> userById = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .map(m -> MemberVO.from(m, userById.get(m.getUserId())))
                .toList();
    }

    @Override
    @Transactional
    public MemberVO add(Long projectId, MemberAddRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 物理查询（忽略逻辑删除）：唯一索引 uk_member_project_user 不含 deleted，
        // 复加曾被软删的成员必须复用旧行，否则 insert 撞唯一索引报 500。
        ProjectMember any = projectMemberMapper.selectAnyByProjectAndUser(projectId, user.getId());
        if (any != null && (any.getDeleted() == null || any.getDeleted() == 0)) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户已是项目成员");
        }
        if (any != null) {
            // 命中软删行：恢复并更新角色。
            projectMemberMapper.restore(any.getId(), request.role());
            any.setDeleted(0);
            any.setRole(request.role());
            return MemberVO.from(any, user);
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setRole(request.role());
        projectMemberMapper.insert(member);
        return MemberVO.from(member, user);
    }

    @Override
    @Transactional
    public void remove(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "项目不存在");
        }
        if (project.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能移除项目所有者");
        }
        int deleted = projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该用户不是项目成员");
        }
    }
}
