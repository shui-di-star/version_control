package com.example.version_control_system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 操作日志表 t_operation_log（只追加、无软删除列）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_operation_log")
public class OperationLog extends BaseEntity {

    private Long projectId;
    private Long userId;
    /** 操作类型（如 CREATE_ENTITY）。 */
    private String action;
    private String targetType;
    private Long targetId;
    /** 操作详情快照 JSON。 */
    private String detail;
}
