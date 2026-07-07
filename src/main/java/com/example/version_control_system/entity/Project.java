package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目表 t_project。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_project")
public class Project extends BaseEntity {

    private String name;
    private String description;
    /** 创建者（t_user.id）。 */
    private Long ownerId;

    @TableLogic
    private Integer deleted;
}
