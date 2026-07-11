package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 实体类型模板表 t_entity_template。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_entity_template")
public class EntityTemplate extends BaseEntity {

    private Long projectId;
    private String name;
    /** 自定义字段定义 JSON（见 §3.3）。 */
    private String fieldSchema;

    @TableLogic
    private Integer deleted;
}
