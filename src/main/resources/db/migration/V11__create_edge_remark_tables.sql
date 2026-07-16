-- 连线备注系统：支持多条备注 + 每条备注多张图片
-- 备注归属子节点（entity_id = 子节点 ID），子节点删除时级联清理。

-- ============================================================
-- t_edge_remark — 连线备注（一个子节点可有多条）
-- ============================================================
CREATE TABLE t_edge_remark (
    id              BIGINT       NOT NULL COMMENT '备注 ID（雪花）',
    entity_id       BIGINT       NOT NULL COMMENT '子节点实体 ID（t_entity.id）',
    project_id      BIGINT       NOT NULL COMMENT '所属项目',
    content         TEXT         NULL     COMMENT '备注文本内容',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_edge_remark_entity (entity_id),
    KEY idx_edge_remark_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '连线备注';

-- ============================================================
-- t_edge_remark_image — 备注图片
-- ============================================================
CREATE TABLE t_edge_remark_image (
    id              BIGINT       NOT NULL COMMENT '图片 ID（雪花）',
    remark_id       BIGINT       NOT NULL COMMENT '所属备注（t_edge_remark.id）',
    file_name       VARCHAR(255) NOT NULL COMMENT '原始文件名',
    object_key      VARCHAR(512) NOT NULL COMMENT 'MinIO 对象 key',
    size            BIGINT       NULL     COMMENT '文件字节数',
    mime_type       VARCHAR(128) NULL     COMMENT 'MIME 类型',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_remark_image_remark (remark_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '连线备注图片';

-- ============================================================
-- 数据迁移：将现有 parent_relation_remark 迁移为 t_edge_remark 首条记录
-- 使用 entity.id + 1000000000 作为 remark id（避免与雪花ID冲突概率极低）
-- ============================================================
INSERT INTO t_edge_remark (id, entity_id, project_id, content, sort_order, created_at, updated_at, created_by, deleted)
SELECT
    e.id + 1000000000000000000,
    e.id,
    e.project_id,
    e.parent_relation_remark,
    0,
    e.created_at,
    e.updated_at,
    e.created_by,
    0
FROM t_entity e
WHERE e.parent_relation_remark IS NOT NULL
  AND e.parent_relation_remark != ''
  AND e.deleted = 0;
