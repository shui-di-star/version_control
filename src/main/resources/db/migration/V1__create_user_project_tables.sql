-- 步骤 2.2：用户与项目表
-- t_user / t_project / t_project_member
-- 约定（design-document.md §3.2）：id 雪花(BIGINT)、created_at/updated_at/created_by、软删除表加 deleted(TINYINT)；
-- 无物理外键（决策 9），引用完整性由 Service 层校验；字符集 utf8mb4。

-- ============================================================
-- t_user — 用户
-- ============================================================
CREATE TABLE t_user (
    id                    BIGINT       NOT NULL COMMENT '用户 ID（雪花）',
    username              VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash         VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    email                 VARCHAR(128) NULL     COMMENT '邮箱',
    display_name          VARCHAR(64)  NULL     COMMENT '显示名',
    system_role           VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '全局角色：SUPER_ADMIN / USER',
    status                TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用 / 1 启用',
    token_invalid_before  DATETIME     NULL     COMMENT '登出时间戳：iat 早于此值的 JWT 视为失效（决策 6）',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- ============================================================
-- t_project — 项目
-- ============================================================
CREATE TABLE t_project (
    id           BIGINT        NOT NULL COMMENT '项目 ID（雪花）',
    name         VARCHAR(128)  NOT NULL COMMENT '项目名称',
    description  VARCHAR(512)  NULL     COMMENT '描述',
    owner_id     BIGINT        NOT NULL COMMENT '创建者（t_user.id）',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   BIGINT        NULL     COMMENT '创建人（t_user.id）',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_project_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目';

-- ============================================================
-- t_project_member — 项目成员（项目级权限核心）
-- ============================================================
CREATE TABLE t_project_member (
    id           BIGINT       NOT NULL COMMENT '成员记录 ID（雪花）',
    project_id   BIGINT       NOT NULL COMMENT '项目 ID',
    user_id      BIGINT       NOT NULL COMMENT '用户 ID',
    role         VARCHAR(16)  NOT NULL COMMENT '项目角色：ADMIN / EDITOR / VIEWER',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   BIGINT       NULL     COMMENT '创建人（t_user.id）',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_project_user (project_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目成员';
