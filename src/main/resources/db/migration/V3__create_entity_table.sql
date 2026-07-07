-- 步骤 2.4：实体表（单父树核心）
-- t_entity
-- 约定（design-document.md §3.2）：id 雪花(BIGINT)、created_at/updated_at/created_by、软删除加 deleted(TINYINT)；
-- 无物理外键（决策 9）：parent_id / template_id 合法性由 Service 层校验（步骤 6.1），不在建表层约束。
-- 单父树红线：迭代结构只由 parent_id 表达（NULL=根）；跨分支语义关系走 t_relation，不入本表。

-- ============================================================
-- t_entity — 实体（迭代节点，单父树核心）
-- parent_id：父节点；NULL 表示根节点 → 决定树结构
-- status：互斥单选 RECOMMENDED / DEPRECATED / SIMULATING / COMPLETED，空=无（决策 3）
--         COMPLETED = 已完成仿真但既不推荐也不淘汰，同样计入"已完成仿真"统计口径
-- attributes：自定义字段值（键对应模板 field_schema，见 §3.3）
-- ============================================================
CREATE TABLE t_entity (
    id            BIGINT        NOT NULL COMMENT '实体 ID（雪花）',
    project_id    BIGINT        NOT NULL COMMENT '所属项目',
    template_id   BIGINT        NOT NULL COMMENT '实体类型（t_entity_template.id）',
    parent_id     BIGINT        NULL     COMMENT '父节点；NULL 表示根节点，决定树结构',
    name          VARCHAR(128)  NOT NULL COMMENT '实体名称',
    status        VARCHAR(16)   NULL     COMMENT '状态标记（互斥单选）：RECOMMENDED/DEPRECATED/SIMULATING/COMPLETED，空=无',
    is_milestone  TINYINT       NOT NULL DEFAULT 0 COMMENT '是否里程碑节点：0 否 / 1 是',
    remark        TEXT          NULL     COMMENT '备注（搜索目标之一）',
    attributes    JSON          NULL     COMMENT '自定义字段值（键对应模板 field_schema，见 §3.3）',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT        NULL     COMMENT '创建人（t_user.id）',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_entity_project_parent (project_id, parent_id),
    KEY idx_entity_template (template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实体（迭代节点，单父树核心）';
