package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目成员表 t_project_member（项目级权限核心）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_project_member")
public class ProjectMember extends BaseEntity {

    private Long projectId;
    private Long userId;
    /** 项目角色：ADMIN / EDITOR / VIEWER。 */
    private String role;

    @TableLogic
    private Integer deleted;
}
