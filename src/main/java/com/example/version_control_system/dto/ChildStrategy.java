package com.example.version_control_system.dto;

/** 删除实体时的子节点处理策略。 */
public enum ChildStrategy {
    /** 递归软删整棵子树及其关联关系/产出物。 */
    CASCADE,
    /** 仅删本节点，直接子节点上提至被删节点的父节点。 */
    PROMOTE
}
