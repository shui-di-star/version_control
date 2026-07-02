# 仿真版本管理软件 — 产品设计文档（Design Document）

> 版本：v1.0
> 日期：2026-07-02
> 状态：设计定稿，待评审进入开发

---

## 1. 文档说明

本文档基于 `PRD.md` 及需求讨论结论编写，是后续开发的技术依据。文档聚焦**架构设计、数据模型、接口设计、模块划分与开发计划**，不重复 PRD 的业务背景。

### 1.1 需求讨论确定的关键决策

| 决策项 | 结论 | 说明 |
| --- | --- | --- |
| 部署形态 | Web 应用 | 家里开发 → 打包 → 公司内网服务器部署，浏览器访问 |
| 用户规模 | 团队多人 | 需完整鉴权（Admin/Editor/Viewer）+ 项目级权限隔离 |
| 迭代树结构 | **单父树** | `t_entity.parent_id` 决定树；`t_relation` 仅承载非父子语义关系 |
| 实体属性 | **完整动态属性模板** | 用户自定义实体类型及字段，JSON 存储 |
| 产出物预览 | **初期只下载** | 在线预览后续迭代实现 |
| 文件存储 | MinIO 对象存储 | 数据库仅存元信息与访问 key |
| 数据库 | MySQL 8.0 | 只做单目标，不做 PostgreSQL 兼容 |
| ORM | MyBatis-Plus | CRUD 简洁，递归 CTE 与 JSON 查询直接 |
| 树图库 | AntV G6 v5 | compactBox 自上而下布局 |
| 节点位置 | **自动布局** | 不持久化手拖坐标；PRD "拖动自动保存"取消 |

### 1.2 设计原则

- **单父树优先**：迭代主干由父子关系唯一确定，树布局与路径追溯逻辑简单可靠。
- **动态属性以 JSON 承载**：实体类型/关系类型/字段定义存模板表，实体实例的自定义字段值存 JSON 列。
- **分层清晰**：Controller → Service → Mapper，DTO/VO 与 Entity 分离。
- **单目标不做兼容层**：只针对 MySQL 8.0，直接使用其 `json` 类型与 `WITH RECURSIVE`。

---

## 2. 系统架构

### 2.1 总体架构

```
┌─────────────────────────────────────────────────────┐
│                     浏览器（Chrome/Edge）              │
│  React 18 + TS + Vite + AntD 5 + G6 v5 + Zustand      │
└───────────────────────────┬─────────────────────────┘
                            │ HTTPS / REST (JSON)
                            │ Authorization: Bearer <JWT>
┌───────────────────────────▼─────────────────────────┐
│              Spring Boot（Java 21）后端               │
│  ┌────────────┐ ┌───────────┐ ┌──────────────────┐  │
│  │ Controller │→│  Service  │→│ Mapper(MyBatis+)  │  │
│  └────────────┘ └───────────┘ └──────────────────┘  │
│  Spring Security + JWT ｜ 全局异常 ｜ AOP 操作日志     │
└──────────┬────────────────────────────┬─────────────┘
           │                            │
     ┌─────▼──────┐              ┌───────▼───────┐
     │  MySQL 8.0 │              │  MinIO（对象） │
     │ (业务数据)  │              │  (产出物文件)  │
     └────────────┘              └───────────────┘
```

### 2.2 技术栈（定稿）

**后端**
- Spring Boot 3.x（Java 21）
- MyBatis-Plus（持久层）
- MySQL 8.0（`json` 类型 + `WITH RECURSIVE` 递归 CTE）
- Spring Security + JWT（jjwt）
- Flyway（数据库版本迁移）
- MinIO Java SDK（对象存储）
- SpringDoc / Swagger UI（API 文档）
- Hibernate Validator（参数校验）
- Lombok、MapStruct、Hutool（辅助）

> 说明：PRD 提到"Spring Boot 4.x"，当前 Spring Boot 稳定版为 3.x，Spring Boot 4 尚未 GA，故采用 3.x（Java 21）。若开发期 4.x 已 GA 可再评估升级。

**前端**
- React 18 + TypeScript + Vite
- Ant Design 5（管理后台 UI）
- AntV G6 v5（树图可视化）
- Zustand（状态管理）
- Axios（HTTP 客户端，统一拦截 JWT 与错误）
- React Router（路由）

### 2.3 后端工程结构

```
src/main/java/com/sim/versionmgr/
├── VersionMgrApplication.java
├── config/            # Security、CORS、MinIO、Swagger、MyBatis-Plus 配置
├── controller/        # REST API
├── service/ + impl/   # 业务逻辑
├── mapper/            # MyBatis-Plus Mapper（含自定义递归 CTE SQL）
├── entity/            # 数据库实体（与表对应）
├── dto/               # 请求/响应传输对象
├── vo/                # 视图对象（如树节点 VO）
├── common/
│   ├── result/        # Result<T> 统一响应
│   ├── exception/     # 全局异常处理
│   └── enums/         # 角色、产出物类型、实体状态等枚举
├── security/          # JWT 过滤器、UserDetails、权限注解
└── aspect/            # AOP 操作日志
resources/
├── db/migration/      # Flyway SQL 脚本 V1__xxx.sql
└── application.yml
```

---

## 3. 数据模型设计

### 3.1 ER 概览

```
t_user ──< t_project_member >── t_project
                                    │
        ┌───────────────┬──────────┼──────────────┐
        │               │          │              │
 t_entity_template  t_relation_template  t_entity  t_operation_log
        │               │          │
        │(type)         │(type)    ├──< t_asset (产出物)
        │               │          │
        └──────┐        │          ├── parent_id (自引用，单父树)
               ▼        ▼          │
             t_entity   t_relation ┘（额外语义关系：from_entity → to_entity）
```

### 3.2 表结构定义

约定：所有表含 `id`(BIGINT PK 雪花/自增)、`created_at`、`updated_at`、`created_by`；软删除表额外含 `deleted`(TINYINT)。

#### t_user — 用户

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 用户 ID |
| username | VARCHAR(64) UNIQUE | 登录名 |
| password_hash | VARCHAR(100) | BCrypt 加密密码 |
| email | VARCHAR(128) | 邮箱 |
| display_name | VARCHAR(64) | 显示名 |
| system_role | VARCHAR(16) | 全局角色：SUPER_ADMIN / USER。SUPER_ADMIN 为跨项目超级管理员 |
| status | TINYINT | 0 禁用 / 1 启用 |

> **跨项目超级管理员**：`system_role = SUPER_ADMIN` 的用户拥有所有项目的最高权限，无需在 `t_project_member` 登记即可访问/管理任意项目，并可管理全部用户与项目。权限校验时先判断 system_role，命中 SUPER_ADMIN 直接放行。

#### t_project — 项目

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 项目 ID |
| name | VARCHAR(128) | 项目名称 |
| description | VARCHAR(512) | 描述 |
| owner_id | BIGINT | 创建者（t_user.id） |

#### t_project_member — 项目成员（项目级权限核心）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | 项目 ID |
| user_id | BIGINT | 用户 ID |
| role | VARCHAR(16) | ADMIN / EDITOR / VIEWER |

> 唯一索引 `(project_id, user_id)`。**项目级权限完全由本表决定**：用户能访问哪些项目、在项目内是何角色，均查此表。全局系统管理员另由 `t_user` 层面区分（可选）。

#### t_entity_template — 实体类型模板

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | 所属项目（模板项目内隔离） |
| name | VARCHAR(64) | 类型名称（如"仿真方案"） |
| icon | VARCHAR(64) | 图标标识 |
| color | VARCHAR(16) | 颜色（如 #1890ff） |
| field_schema | JSON | 自定义字段定义（见 3.3） |

#### t_relation_template — 关系类型模板

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | 所属项目 |
| name | VARCHAR(64) | 关系名称（如"参考自"） |
| directed | TINYINT | 是否有向 |
| line_style | JSON | 线条样式 {color, dash: bool, width} |
| allowed_from | JSON | 允许的源实体类型 id 数组（约束，可空=不限） |
| allowed_to | JSON | 允许的目标实体类型 id 数组 |

#### t_entity — 实体（迭代节点，单父树核心）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | 所属项目 |
| template_id | BIGINT | 实体类型（t_entity_template.id） |
| parent_id | BIGINT NULL | **父节点；NULL 表示根节点 → 决定树结构** |
| name | VARCHAR(128) | 实体名称 |
| status | VARCHAR(16) NULL | 状态标记（**互斥单选**）：RECOMMENDED/DEPRECATED/SIMULATING，空=无 |
| is_milestone | TINYINT | 是否里程碑节点 |
| remark | TEXT | 备注（搜索目标之一） |
| attributes | JSON | 自定义字段值（键对应模板 field_schema，见 3.3） |

> 索引：`(project_id, parent_id)` 加速树查询。`parent_id` 外键指向自身 `id`。

#### t_relation — 额外语义关系（非父子）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | 所属项目 |
| template_id | BIGINT | 关系类型 |
| from_entity_id | BIGINT | 源实体 |
| to_entity_id | BIGINT | 目标实体 |
| remark | TEXT | 关系备注（搜索目标之一） |

> **重要**：迭代主干父子关系 **不** 存本表，只由 `t_entity.parent_id` 表达。本表仅记录跨分支的额外语义关系（如"方案 D 参考自方案 B"），在树图上以不同样式的附加连线叠加显示。

#### t_asset — 产出物

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| entity_id | BIGINT | 所属实体（一个实体挂多个产出物） |
| asset_type | VARCHAR(16) | PPT/DOC/SHEET/IMAGE/ANIMATION/TEXT |
| file_name | VARCHAR(255) | 原始文件名 |
| object_key | VARCHAR(512) | MinIO 对象 key |
| content_text | TEXT NULL | 文字类产出物内联内容（TEXT 类型时用） |
| size | BIGINT | 文件字节数 |
| mime_type | VARCHAR(128) | MIME 类型 |

#### t_operation_log — 操作日志

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| project_id | BIGINT | |
| user_id | BIGINT | 操作人 |
| action | VARCHAR(64) | 操作类型（CREATE_ENTITY 等） |
| target_type | VARCHAR(32) | 目标类型 |
| target_id | BIGINT | 目标 ID |
| detail | JSON | 操作详情快照 |

### 3.3 动态属性（JSON）设计

**模板字段定义**（`t_entity_template.field_schema`）：

```json
{
  "fields": [
    { "key": "mesh_size",   "label": "网格尺寸", "type": "NUMBER", "required": true, "unit": "mm" },
    { "key": "solver",      "label": "求解器",   "type": "ENUM",   "options": ["Abaqus","Ansys","Nastran"] },
    { "key": "description", "label": "说明",     "type": "TEXT",   "multiline": true },
    { "key": "run_date",    "label": "运行日期", "type": "DATE" }
  ]
}
```

字段类型：`TEXT` / `NUMBER` / `ENUM`（单选/多选）/ `DATE` / `FILE`（关联产出物）。

**实体属性值**（`t_entity.attributes`）：

```json
{
  "mesh_size": 2.5,
  "solver": "Abaqus",
  "description": "第二轮细化网格",
  "run_date": "2026-06-30"
}
```

> 统计面板中"实体各属性最大值"等聚合：对 `NUMBER` 类型字段用 MySQL `JSON_EXTRACT` / `->>'$.key'` 提取后聚合。前端也可在获取全树后本地计算。

---

## 4. 核心逻辑设计

### 4.1 树查询（单父树 + 递归 CTE）

获取整棵树（`GET /api/projects/{pid}/entities/tree`）：

```sql
WITH RECURSIVE entity_tree AS (
    SELECT id, parent_id, name, template_id, status, is_milestone, 0 AS depth
    FROM t_entity
    WHERE project_id = #{pid} AND parent_id IS NULL AND deleted = 0
  UNION ALL
    SELECT e.id, e.parent_id, e.name, e.template_id, e.status, e.is_milestone, t.depth + 1
    FROM t_entity e
    JOIN entity_tree t ON e.parent_id = t.id
    WHERE e.deleted = 0
)
SELECT * FROM entity_tree;
```

后端将平铺结果组装为嵌套树结构 VO 返回；G6 用 compactBox（rankdir=TB）自上而下渲染。

路径追溯（`GET /api/entities/{id}/path`，根 → 当前节点）：

```sql
WITH RECURSIVE up_path AS (
    SELECT id, parent_id, name FROM t_entity WHERE id = #{id}
  UNION ALL
    SELECT e.id, e.parent_id, e.name
    FROM t_entity e JOIN up_path p ON e.id = p.parent_id
)
SELECT * FROM up_path;
```

### 4.2 删除实体的级联处理

删除节点时提供**两种策略，由用户在删除弹窗中选择**（接口以参数传入，如 `childStrategy=CASCADE|PROMOTE`）：

- **子树全删（CASCADE）**：递归删除该节点及其所有子孙。
  1. 递归 CTE 查出子树全部 id。
  2. 删除这些实体关联的 `t_relation`（from/to 命中）、`t_asset`（含 MinIO 对象）。
  3. 软删除子树全部实体。
- **子节点上提（PROMOTE）**：仅删除该节点本身，其直接子节点的 `parent_id` 改为被删节点的 `parent_id`（上提一层；若删的是根节点，子节点变为新的根，`parent_id = NULL`）。该节点自身关联的 relation/asset 一并删除。

删除前弹窗提示受影响节点数，并让用户选择上述策略。

### 4.3 权限校验模型

两层校验：

1. **认证**：Spring Security + JWT。登录签发 JWT（含 userId、username），后续请求经 JwtAuthenticationFilter 解析。
2. **项目级授权**：自定义切面/注解校验当前用户在目标 project 的角色。

角色权限矩阵：

| 操作 | SuperAdmin | Admin | Editor | Viewer |
| --- | --- | --- | --- | --- |
| 查看项目内容 | ✓ | ✓ | ✓ | ✓ |
| 增删改实体/关系/产出物 | ✓ | ✓ | ✓ | ✗ |
| 模板管理 | ✓ | ✓ | ✗ | ✗ |
| 项目设置/成员管理 | ✓ | ✓ | ✗ | ✗ |
| 删除项目 | ✓ | ✓(owner) | ✗ | ✗ |
| 用户管理 / 访问任意项目 | ✓ | ✗ | ✗ | ✗ |

> SuperAdmin（`t_user.system_role = SUPER_ADMIN`）为全局超级管理员，跨所有项目拥有最高权限；Admin/Editor/Viewer 为项目级角色（`t_project_member.role`）。校验顺序：先判 SuperAdmin 放行，否则查项目成员角色。

实现方式：自定义注解 `@RequireProjectRole(EDITOR)`，切面从路径 `{pid}` 或请求体解析 projectId；若当前用户是 SuperAdmin 直接放行，否则查 `t_project_member` 校验角色 ≥ 要求。

### 4.4 自动保存

- 编辑操作（增删改实体/关系/属性/备注）前端**防抖**（约 800ms）后调用后端接口。
- 保存状态在底部状态栏提示："保存中…" / "已保存"。
- 节点位置**不保存**（自动布局），故拖动仅本地视图效果，刷新后按算法重排。

### 4.5 搜索与高亮

- 后端：`GET /api/projects/{pid}/search?keyword=` 对 `t_entity.remark`、`t_entity.name`、`t_relation.remark` 做 `LIKE` 模糊查询，返回命中列表（含 entityId/relationId、匹配片段）。
- 前端：结果列表点击 → G6 `focusItem` 居中定位 + 高亮该节点/连线。
- 数据量大时二期引入 Elasticsearch（本期不做）。

---

## 5. REST API 设计

统一响应：`Result<T> { code, message, data }`，统一前缀 `/api`，认证用 `Authorization: Bearer <JWT>`。

| 模块 | 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- | --- |
| 认证 | POST | /api/auth/register | 公开 | 注册 |
| 认证 | POST | /api/auth/login | 公开 | 登录取 JWT |
| 认证 | POST | /api/auth/logout | 登录 | 登出 |
| 认证 | GET | /api/auth/me | 登录 | 当前用户信息 |
| 项目 | GET | /api/projects | 登录 | 我参与的项目列表（含角色） |
| 项目 | POST | /api/projects | 登录 | 创建项目（创建者=Admin） |
| 项目 | PUT | /api/projects/{id} | Admin | 编辑项目 |
| 项目 | DELETE | /api/projects/{id} | owner | 删除项目 |
| 成员 | GET | /api/projects/{id}/members | 成员 | 成员列表 |
| 成员 | POST | /api/projects/{id}/members | Admin | 邀请成员/分配角色 |
| 成员 | DELETE | /api/projects/{id}/members/{uid} | Admin | 移除成员 |
| 模板 | GET/POST/PUT/DELETE | /api/projects/{pid}/entity-templates | 读:成员 写:Admin | 实体模板 CRUD |
| 模板 | GET/POST/PUT/DELETE | /api/projects/{pid}/relation-templates | 读:成员 写:Admin | 关系模板 CRUD |
| 实体 | GET | /api/projects/{pid}/entities/tree | 成员 | 获取整棵树 |
| 实体 | POST/PUT/DELETE | /api/projects/{pid}/entities | 读:成员 写:Editor+ | 实体增删改 |
| 实体 | PUT | /api/entities/{id}/milestone | Editor+ | 切换里程碑 |
| 实体 | PUT | /api/entities/{id}/status | Editor+ | 设置状态标记 |
| 实体 | GET | /api/entities/{id}/path | 成员 | 根→节点路径 |
| 关系 | GET/POST/PUT/DELETE | /api/projects/{pid}/relations | 读:成员 写:Editor+ | 语义关系 CRUD |
| 产出物 | POST | /api/entities/{id}/assets | Editor+ | 上传产出物（MultipartFile） |
| 产出物 | GET | /api/entities/{id}/assets | 成员 | 产出物列表 |
| 产出物 | GET | /api/assets/{id}/download | 成员 | 下载（MinIO 预签名 URL 或流式） |
| 产出物 | DELETE | /api/assets/{id} | Editor+ | 删除产出物 |
| 搜索 | GET | /api/projects/{pid}/search?keyword= | 成员 | 搜索备注/名称 |
| 统计 | GET | /api/projects/{pid}/stats | 成员 | 统计面板数据 |
| 导入导出 | GET | /api/projects/{pid}/export | Admin | 导出项目 JSON |
| 导入导出 | POST | /api/projects/{pid}/import | Admin | 导入项目 JSON |
| 日志 | GET | /api/projects/{pid}/logs | Admin | 操作日志 |

---

## 6. 前端设计

### 6.1 页面结构

```
App
├── 登录 / 注册页
├── 主布局（顶部导航：项目切换 / 全局搜索 / 用户信息）
│   ├── 项目列表页（多项目、成员管理入口）
│   ├── 主页面（树状迭代视图）
│   │   ├── G6 树图画布（compactBox 自上而下、展开/折叠、缩放、小地图）
│   │   ├── 左侧工具栏（新增实体/关系、展开/折叠全部、过滤器）
│   │   ├── 右侧详情面板（属性/产出物/备注/里程碑/状态标记）
│   │   ├── 顶部状态过滤（全部/推荐/淘汰/仿真中）+ 统计面板
│   │   └── 底部状态栏（保存状态）+ 搜索结果浮层
│   ├── 内容管理页（实体模板 Tab / 关系模板 Tab）
│   └── 设置页（项目设置 / 成员权限 / 导入导出 / 操作日志）
```

### 6.2 状态管理（Zustand）

- `authStore`：token、当前用户。
- `projectStore`：项目列表、当前项目、当前用户在该项目的角色（驱动前端按钮禁用）。
- `treeStore`：树数据、选中节点、过滤条件、搜索命中。

### 6.3 G6 树图要点

- 布局：`compactBox`，`direction: 'TB'`（自上而下）。
- 节点：按实体类型模板的 icon/color 渲染；里程碑加星标；状态标记（推荐/淘汰/仿真中）用边框/高亮色区分；折叠时显示子节点数量徽标。
- 连线：父子为主干实线；`t_relation` 语义关系按关系模板 line_style（颜色/虚实）叠加显示。
- 交互：展开/折叠、缩放拖拽画布、搜索 focus 定位、路径追溯高亮。

### 6.4 过滤器

- 按实体类型显隐节点、按关系类型显隐连线、按里程碑筛选、按状态标记筛选，多条件组合。前端在已加载的全树上做显隐，不额外请求。

---

## 7. 非功能性需求

| 需求 | 设计措施 |
| --- | --- |
| 性能 | 1000+ 节点：G6 折叠机制 + 树查询递归 CTE；一次性拉全树在前端组装 |
| 数据安全 | 自动保存 + MySQL 持久化；项目级权限隔离（t_project_member） |
| 安全性 | BCrypt 密码；JWT 鉴权；接口 @RequireProjectRole 校验；文件类型/大小校验 |
| 文件限制 | 单文件 ≤ 100MB（Spring `max-file-size` + MinIO） |
| 兼容性 | Chrome / Edge 主流版本 |
| 跨域 | 后端 CORS 允许前端域名 |
| 可维护性 | Controller-Service-Mapper 分层；Flyway 版本化建表 |

---

## 8. 开发计划（迭代式）

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| 一 | 项目骨架 | 后端 Spring Boot + MyBatis-Plus + MySQL + Swagger 跑通；前端 Vite+React+AntD 骨架 |
| 二 | 认证鉴权 | 注册/登录/登出 + Spring Security + JWT |
| 三 | 多项目 + 成员权限 | 项目 CRUD、成员管理、@RequireProjectRole 切面 |
| 四 | 建表与基础 CRUD | Flyway 脚本（全部表）；模板/实体/关系 CRUD |
| 五 | 内容管理页 | 实体/关系模板增删改（含动态字段 schema 编辑） |
| 六 | 树查询 + G6 渲染 | 递归 CTE 树接口 + 前端自上而下树图、展开折叠 |
| 七 | 树图交互 | 增删实体/关系、详情面板、备注、里程碑、状态标记、统计面板、过滤器 |
| 八 | 产出物 | MinIO 上传/下载/删除，多类型（初期只下载，文字内联展示） |
| 九 | 搜索高亮 | LIKE 搜索 + G6 定位高亮 |
| 十 | 补充功能 | 自动保存、迭代对比、路径追溯、导入导出、操作日志 |

> 二期（不在本期范围）：PPT/Word/Excel 在线预览、Elasticsearch 搜索、节点位置持久化、撤销/重做、DAG 多父迭代。

---

## 9. 已确认设计决策

第 8 节评审确认项已定：

1. **跨项目超级管理员**：需要。`t_user.system_role = SUPER_ADMIN`，跨所有项目最高权限 + 用户/项目管理（见 3.2 t_user、4.3 权限矩阵）。
2. **删除实体子节点处理**：由用户在删除弹窗中选择——子树全删（CASCADE）或子节点上提（PROMOTE），接口以 `childStrategy` 参数传入（见 4.2）。
3. **实体状态标记**：互斥单选（推荐/淘汰/仿真中三选一或无，见 3.2 t_entity.status）。
4. **导入 JSON id 冲突**：导入时**一律生成新 id**。导入流程为项目内所有实体/模板/关系分配新 id，并按旧 id → 新 id 映射表重建 `parent_id`、`template_id`、`from_entity_id`、`to_entity_id` 等引用，避免与现有数据冲突。