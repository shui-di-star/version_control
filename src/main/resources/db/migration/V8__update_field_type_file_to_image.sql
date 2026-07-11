-- Phase 2: FILE → IMAGE
-- 批量替换已有 field_schema 中的 "type":"FILE" 为 "type":"IMAGE"
UPDATE t_entity_template
SET field_schema = REPLACE(field_schema, '"type":"FILE"', '"type":"IMAGE"')
WHERE field_schema LIKE '%"type":"FILE"%';

-- 也处理可能带空格的情况
UPDATE t_entity_template
SET field_schema = REPLACE(field_schema, '"type": "FILE"', '"type": "IMAGE"')
WHERE field_schema LIKE '%"type": "FILE"%';
