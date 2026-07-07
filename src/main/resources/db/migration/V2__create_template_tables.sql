-- 步骤 2.3：模板表
-- t_entity_template / t_relation_template
-- 约定（design-document.md §3.2）：id 雪花(BIGINT)、created_at/updated_at/created_by、软删除表加 deleted(TINYINT)；
-- 无物理外键（决策 9），引用完整性由 Service 层校验；字符集 utf8mb4；动态字段用 MySQL json 列（§3.3）。

-- ============================================================
-- t_entity_template — 实体类型模板
-- field_schema：自定义字段定义（{ "fields": [ { key, label, type, ... } ] }，见 §3.3）
-- ============================================================
CREATE TABLE t_entity_template (
    id            BIGINT       NOT NULL COMMENT '模板 ID（雪花）',
    project_id    BIGINT       NOT NULL COMMENT '所属项目（模板项目内隔离）',
    name          VARCHAR(64)  NOT NULL COMMENT '类型名称（如"仿真方案"）',
    icon          VARCHAR(64)  NULL     COMMENT '图标标识',
    color         VARCHAR(16)  NULL     COMMENT '颜色（如 #1890ff）',
    field_schema  JSON         NULL     COMMENT '自定义字段定义（见 §3.3）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_entity_template_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实体类型模板';

-- ============================================================
-- t_relation_template — 关系类型模板
-- line_style：线条样式 {color, dash: bool, width}
-- allowed_from / allowed_to：允许的实体类型 id 数组（约束，可空=不限）
-- ============================================================
CREATE TABLE t_relation_template (
    id            BIGINT       NOT NULL COMMENT '模板 ID（雪花）',
    project_id    BIGINT       NOT NULL COMMENT '所属项目',
    name          VARCHAR(64)  NOT NULL COMMENT '关系名称（如"参考自"）',
    directed      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否有向：0 无向 / 1 有向',
    line_style    JSON         NULL     COMMENT '线条样式 {color, dash, width}',
    allowed_from  JSON         NULL     COMMENT '允许的源实体类型 id 数组（可空=不限）',
    allowed_to    JSON         NULL     COMMENT '允许的目标实体类型 id 数组（可空=不限）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_relation_template_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关系类型模板';
