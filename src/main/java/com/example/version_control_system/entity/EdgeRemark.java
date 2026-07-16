package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 连线备注（归属子节点，支持多条）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_edge_remark")
public class EdgeRemark extends BaseEntity {

    private Long entityId;
    private Long projectId;
    private String content;
    private Integer sortOrder;

    @TableLogic
    private Integer deleted;
}
