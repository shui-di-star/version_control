package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 额外语义关系表 t_relation（非父子；父子关系只由 t_entity.parent_id 表达）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_relation")
public class Relation extends BaseEntity {

    private Long projectId;
    private Long templateId;
    private Long fromEntityId;
    private Long toEntityId;
    private String remark;

    @TableLogic
    private Integer deleted;
}
