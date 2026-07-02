# Flyway 数据库迁移脚本

本目录存放 Flyway 版本化迁移脚本，应用启动时由 `spring-boot-starter-flyway` 自动执行。

## 命名规范

版本化脚本（Versioned）：

```
V<版本号>__<描述>.sql
```

- 前缀大写 `V`，版本号用递增整数，双下划线 `__` 分隔描述，描述用下划线代替空格。
- 从 `V1__` 起，按功能顺序递增：
  - `V1__create_user_project_tables.sql`（步骤 2.2：t_user / t_project / t_project_member）
  - `V2__create_template_tables.sql`（步骤 2.3：t_entity_template / t_relation_template）
  - `V3__create_entity_table.sql`（步骤 2.4：t_entity）
  - `V4__create_relation_asset_log_tables.sql`（步骤 2.5：t_relation / t_asset / t_operation_log）
- 版本号一旦发布（被 `flyway_schema_history` 记录成功）**不可修改脚本内容**；修正需新增更高版本脚本。

## 约定（见 design-document.md §3.2）

- 所有表含 `id`(BIGINT PK，雪花 ID)、`created_at`、`updated_at`、`created_by`；软删除表额外含 `deleted`(TINYINT)。
- 使用 MySQL `json` 类型存放动态属性 / 样式 / 详情。
- **不建物理外键**（决策 9）：`parent_id`、`template_id`、关系端点等引用完整性由 Service 层校验。
- 字符集 utf8mb4。

## 本地重建

从零重建开发库后，下次启动 Flyway 会自动跑全部脚本。测试库 `vcs_test` 同理（集成测试前重建）。
