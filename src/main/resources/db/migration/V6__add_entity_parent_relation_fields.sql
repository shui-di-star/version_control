-- V6: 为父子关系增加关系类型字段
ALTER TABLE t_entity ADD COLUMN parent_relation_template_id BIGINT NULL COMMENT '与父节点的关系模板ID';
ALTER TABLE t_entity ADD COLUMN parent_relation_remark TEXT NULL COMMENT '与父节点的关系备注';
