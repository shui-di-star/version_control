package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 关系类型模板表 t_relation_template。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_relation_template")
public class RelationTemplate extends BaseEntity {

    private Long projectId;
    private String name;
    /** 是否有向：0 无向 / 1 有向。 */
    private Integer directed;
    /** 线条样式 JSON {color, dash, width}。 */
    private String lineStyle;
    /** 允许的源实体类型 id 数组 JSON（可空=不限）。 */
    private String allowedFrom;
    /** 允许的目标实体类型 id 数组 JSON（可空=不限）。 */
    private String allowedTo;

    @TableLogic
    private Integer deleted;
}
