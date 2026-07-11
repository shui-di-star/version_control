-- 属性图片引用计数表：同一 objectKey 可被多个实体引用（复制卡片场景）
CREATE TABLE t_attr_image_ref (
    id          BIGINT       NOT NULL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    object_key  VARCHAR(512) NOT NULL,
    ref_count   INT          NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_air_project_key (project_id, object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
