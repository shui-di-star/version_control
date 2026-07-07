-- V7: 为已有父子关系补"迭代"关系模板
-- 逻辑：对每个含有 parent_id IS NOT NULL 实体的项目，若无名为"迭代"的关系模板则插入一条，
-- 然后将该项目下 parent_relation_template_id IS NULL 的子实体全部关联到该模板。

-- 步骤1：为每个需要的 project_id 插入"迭代"关系模板（仅当该项目还没有同名模板时）
INSERT INTO t_relation_template (id, project_id, name, directed, line_style, created_at, updated_at, deleted)
SELECT UUID_SHORT(),
       p.project_id,
       '迭代',
       1,
       '{"color":"#1890ff","dash":false,"width":2}',
       NOW(),
       NOW(),
       0
FROM (
    SELECT DISTINCT e.project_id
    FROM t_entity e
    WHERE e.parent_id IS NOT NULL
      AND e.deleted = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM t_relation_template rt
    WHERE rt.project_id = p.project_id
      AND rt.name = '迭代'
      AND rt.deleted = 0
);

-- 步骤2：将所有缺失 parent_relation_template_id 的子实体回填为对应项目的"迭代"模板
UPDATE t_entity e
INNER JOIN t_relation_template rt
    ON rt.project_id = e.project_id
   AND rt.name = '迭代'
   AND rt.deleted = 0
SET e.parent_relation_template_id = rt.id
WHERE e.parent_id IS NOT NULL
  AND e.parent_relation_template_id IS NULL
  AND e.deleted = 0;
