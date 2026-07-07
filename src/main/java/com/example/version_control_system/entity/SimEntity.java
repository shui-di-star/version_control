package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 实体表 t_entity（迭代节点，单父树核心）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_entity")
public class SimEntity extends BaseEntity {

    private Long projectId;
    private Long templateId;
    /** 父节点；NULL 表示根节点，决定树结构。PROMOTE 删根时需将子节点 parent_id 置空，故 updateById 也写 null。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String name;
    /** 互斥单选 RECOMMENDED/DEPRECATED/SIMULATING/COMPLETED，空=无。COMPLETED=已完成仿真但不推荐也不淘汰。清空状态需写 null。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    /** 是否里程碑：0 否 / 1 是。 */
    private Integer isMilestone;
    private String remark;
    /** 自定义字段值 JSON（键对应模板 field_schema，见 §3.3）。 */
    private String attributes;

    /** 与父节点的关系模板 ID（创建子节点时必选）。 */
    private Long parentRelationTemplateId;
    /** 与父节点的关系备注（可选）。 */
    private String parentRelationRemark;

    @TableLogic
    private Integer deleted;
}
