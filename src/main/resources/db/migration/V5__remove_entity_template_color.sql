-- V5: 移除实体模板的 color 字段（节点颜色改由状态决定）
ALTER TABLE t_entity_template DROP COLUMN color;
