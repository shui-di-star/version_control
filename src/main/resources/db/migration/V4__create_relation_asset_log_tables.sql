-- 步骤 2.5：关系、产出物、日志表
-- t_relation / t_asset / t_operation_log
-- 约定（design-document.md §3.2）：id 雪花(BIGINT)、created_at/updated_at/created_by、软删除表加 deleted(TINYINT)；
-- 无物理外键（决策 9），引用完整性由 Service 层校验；字符集 utf8mb4。

-- ============================================================
-- t_relation — 额外语义关系（非父子）
-- 重要：迭代主干父子关系不存本表，只由 t_entity.parent_id 表达；
-- 本表仅记录跨分支的额外语义关系（如"方案 D 参考自方案 B"）。
-- ============================================================
CREATE TABLE t_relation (
    id              BIGINT       NOT NULL COMMENT '关系 ID（雪花）',
    project_id      BIGINT       NOT NULL COMMENT '所属项目',
    template_id     BIGINT       NOT NULL COMMENT '关系类型（t_relation_template.id）',
    from_entity_id  BIGINT       NOT NULL COMMENT '源实体（t_entity.id）',
    to_entity_id    BIGINT       NOT NULL COMMENT '目标实体（t_entity.id）',
    remark          TEXT         NULL     COMMENT '关系备注（搜索目标之一）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_relation_project (project_id),
    KEY idx_relation_from (from_entity_id),
    KEY idx_relation_to (to_entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额外语义关系（非父子）';

-- ============================================================
-- t_asset — 产出物（一个实体挂多个产出物）
-- 文件存 MinIO，DB 只存元信息 + object_key；TEXT 类型可内联存 content_text。
-- ============================================================
CREATE TABLE t_asset (
    id            BIGINT        NOT NULL COMMENT '产出物 ID（雪花）',
    entity_id     BIGINT        NOT NULL COMMENT '所属实体（t_entity.id）',
    asset_type    VARCHAR(16)   NOT NULL COMMENT '类型：PPT/DOC/SHEET/IMAGE/ANIMATION/TEXT',
    file_name     VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    object_key    VARCHAR(512)  NULL     COMMENT 'MinIO 对象 key（TEXT 内联时可空）',
    content_text  TEXT          NULL     COMMENT '文字类产出物内联内容（TEXT 类型时用）',
    size          BIGINT        NULL     COMMENT '文件字节数',
    mime_type     VARCHAR(128)  NULL     COMMENT 'MIME 类型',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT        NULL     COMMENT '创建人（t_user.id）',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_asset_entity (entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产出物';

-- ============================================================
-- t_operation_log — 操作日志
-- 不含软删除列（日志只追加、不删）；detail 存操作详情快照 JSON。
-- ============================================================
CREATE TABLE t_operation_log (
    id            BIGINT       NOT NULL COMMENT '日志 ID（雪花）',
    project_id    BIGINT       NOT NULL COMMENT '所属项目',
    user_id       BIGINT       NOT NULL COMMENT '操作人（t_user.id）',
    action        VARCHAR(64)  NOT NULL COMMENT '操作类型（如 CREATE_ENTITY）',
    target_type   VARCHAR(32)  NULL     COMMENT '目标类型',
    target_id     BIGINT       NULL     COMMENT '目标 ID',
    detail        JSON         NULL     COMMENT '操作详情快照',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT       NULL     COMMENT '创建人（t_user.id）',
    PRIMARY KEY (id),
    KEY idx_oplog_project (project_id),
    KEY idx_oplog_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';
