package com.example.version_control_system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.version_control_system.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 物理查询成员记录（忽略逻辑删除）。唯一索引 uk_member_project_user 不含 deleted，
     * 复加曾被软删的成员时需先探测软删行以避免撞唯一索引。
     */
    @Select("SELECT * FROM t_project_member WHERE project_id = #{projectId} AND user_id = #{userId} LIMIT 1")
    ProjectMember selectAnyByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /** 恢复软删成员并更新角色（复用旧行，规避唯一索引冲突）。 */
    @Update("UPDATE t_project_member SET deleted = 0, role = #{role} WHERE id = #{id}")
    int restore(@Param("id") Long id, @Param("role") String role);
}
