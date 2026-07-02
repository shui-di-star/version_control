# 仿真版本管理软件 — 实施计划（Implementation Plan）

> 依据：`design-document.md` v1.0
> 用途：给 AI 开发者的分步执行指令。每一步都小而具体，且都附带验证测试。
> 约束：本文件**只写指令与验证方法，不包含任何代码**。

## 使用说明

- 严格**按阶段、按步骤顺序**执行，不要跳步。
- 每一步分为「指令」与「验证」两部分。**「验证」不通过，不得进入下一步。**
- 关键设计红线（贯穿全程，违反即返工）：
  - **单父树**：迭代结构只由 `t_entity.parent_id` 表达；`t_relation` 只存非父子语义关系。禁止 DAG/多父逻辑。
  - **仅 MySQL 8.0**：直接使用 `json` 类型与 `WITH RECURSIVE`。禁止 PostgreSQL 语法与任何数据库兼容层。
  - **动态属性用 JSON**：模板定义存 `field_schema`，实体值存 `t_entity.attributes`。
  - **产出物初期只下载**，不做在线预览。
  - **文件存 MinIO**，数据库只存元信息与 object key。
  - **树图节点自动布局**，不持久化坐标。
- 分层纪律：`Controller → Service(+impl) → Mapper`，DTO/VO 与 Entity 分离，统一响应 `Result<T>`。

---

## 已确认前置决策（开发唯一依据）

> 以下为 2026-07-02 讨论确认的补充决策，优先级高于设计文档中的模糊/矛盾表述。开发时以本节为准。

1. **Spring Boot 版本**：采用 **4.0.7**（`pom.xml` 现值）。所有依赖（MyBatis-Plus、jjwt、SpringDoc、MinIO SDK 等）按 Spring Boot 4 / Spring Framework 7 兼容线选版本。设计文档 §2.2 中"用 3.x"的表述作废。
2. **主键策略**：全表用**雪花 id**（MyBatis-Plus `IdType.ASSIGN_ID`，`BIGINT`）。理由：导入功能可在插入前预生成全部新 id，一次性重建父子/关系引用，无需依赖自增回填顺序。设计文档 §3.2"雪花/自增"二选一在此定为雪花。
3. **实体状态枚举**：仅 `RECOMMENDED / DEPRECATED / SIMULATING`（互斥单选，可空）。统计口径中的**"已完成仿真"= RECOMMENDED 数 + DEPRECATED 数**，不新增 COMPLETED 枚举值。
4. **统计口径**：**所有实体均计为"方案节点"**（不按模板类型筛选）。"NUMBER 属性最大值"针对**指定字段**——`GET /api/projects/{pid}/stats` 需接受一个字段 key 参数（如 `numberFieldKey`），后端用 MySQL JSON 提取该 key 后求最大值。
5. **注册开放**：`/api/auth/register` 面向所有人**自助注册**，无需 SuperAdmin 审批。
6. **后端登出**：`t_user` 增加一列 `token_invalid_before`（时间戳/DATETIME）。登出时置为当前时间；JWT 过滤器校验时，若 token 的签发时间（iat）早于该列值则判为失效并拒绝。**语义为"登出该用户全部会话"**（按用户维度失效，非单设备）。JWT 载荷需含签发时间 iat 以支持比对。
7. **集成测试数据库**：**不使用 Docker/Testcontainers**（用户暂不装 Docker）。改用**本地专用测试库**：在本机 MySQL 8.0.45 上单独建一个库（如 `vcs_test`，与开发库隔离，勿混用），测试连接它、每次跑测试前用 Flyway 重建 schema。因大量使用 `WITH RECURSIVE` 与 `json`，H2 内存库不可用，故必须连真 MySQL。阶段一不依赖数据库可先做；阶段二起的连库测试指向 `vcs_test`。
8. **MinIO 部署方式**：本地开发用 **MinIO 原生 Windows 可执行程序**启动本地服务，**不用 Docker**。（阶段八产出物存储用到，此处澄清以免误以为需要 Docker。）
9. **外键策略**：**不建物理外键，用逻辑外键 + 应用层（Service）校验**。理由：与软删除（`deleted`）冲突，且 MyBatis-Plus 生态惯例。凡计划中"应被外键约束拒绝"的验证，一律改为"应被 Service 层校验拒绝"。
10. **FILE 属性类型**：`field_schema` 中 type=FILE 的字段，其在 `t_entity.attributes` 里的**值为一个 asset id**，指向该实体自身在 `t_asset` 中的一条产出物记录（即"从本实体已上传的产出物中挑一个绑定到该字段"）。校验 attributes 时需确认该 asset id 存在且属于当前实体。

---

## 阶段一：项目骨架与基础设施

### 步骤 1.1 — 确认构建与运行基线
- **指令**：确认使用 Maven Wrapper 可成功启动现有 Spring Boot 骨架应用，并跑通默认测试。确认 Java 版本、Spring Boot 版本与 `pom.xml` 一致。
- **验证**：执行 `./mvnw test` 全部通过；执行 `./mvnw spring-boot:run` 应用能启动并监听默认端口，日志无报错。

### 步骤 1.2 — 校准依赖版本策略
- **指令**：核对 `pom.xml` 中 Spring Boot 版本与设计文档假设（3.x）的差异，与用户确认最终采用的 Spring Boot 主版本；据此确定 MyBatis-Plus、Flyway、jjwt、MinIO SDK、MapStruct、Lombok、Hutool、SpringDoc 各依赖的兼容版本。**在版本未与用户确认前，不要批量引入依赖。**
- **验证**：产出一份「依赖-版本」清单并获得用户确认；`./mvnw dependency:tree` 无版本冲突告警。

### 步骤 1.3 — 引入核心依赖
- **指令**：按已确认清单，将 MyBatis-Plus、MySQL 驱动、Flyway、Spring Security、jjwt、Hibernate Validator、Lombok、MapStruct、Hutool、SpringDoc 依赖加入构建。
- **验证**：`./mvnw clean compile` 成功；应用仍能启动（此时数据库相关可暂用占位配置）。

### 步骤 1.4 — 配置文件与环境分离
- **指令**：建立应用配置，包含数据库连接、MinIO 连接、JWT 密钥与过期时间、文件上传大小上限（单文件 ≤ 100MB）等配置项，敏感值用环境变量/外部配置占位，不硬编码。
- **验证**：本地填入真实 MySQL 8.0 连接后应用能连库启动；缺少必需配置时应用启动应给出清晰错误提示。

### 步骤 1.5 — 统一响应与全局异常
- **指令**：设计统一响应结构 `Result<T> { code, message, data }` 与全局异常处理机制，约定业务错误码规范。
- **验证**：编写一个临时探针接口，正常路径返回标准 `Result`；主动抛出一个业务异常时，全局处理器返回标准错误结构而非堆栈。验证后移除临时探针。

### 步骤 1.6 — 集成 API 文档
- **指令**：接入 SpringDoc/Swagger UI，使后续接口自动生成文档。
- **验证**：启动应用后能访问 Swagger UI 页面，并看到探针（或已有）接口。

---

## 阶段二：数据库建表（Flyway）

> 所有表遵循设计文档 §3.2 约定：含 `id`(BIGINT PK)、`created_at`、`updated_at`、`created_by`；软删除表含 `deleted`(TINYINT)。使用 MySQL `json` 类型。

### 步骤 2.1 — Flyway 基线与迁移目录
- **指令**：配置 Flyway 迁移脚本目录（`resources/db/migration/`），确立版本化脚本命名规范（`V1__xxx.sql` 起）。
- **验证**：应用启动时 Flyway 自动执行迁移；`flyway_schema_history` 表被创建且记录 baseline。

### 步骤 2.2 — 用户与项目表
- **指令**：编写迁移脚本创建 `t_user`（含 `system_role` SUPER_ADMIN/USER、`status`、以及登出用的 `token_invalid_before` DATETIME 可空，见决策 6）、`t_project`（含 `owner_id`）、`t_project_member`（含 `role` ADMIN/EDITOR/VIEWER，`(project_id, user_id)` 唯一索引）。
- **验证**：迁移执行成功；连库检查三张表结构、字段类型、唯一索引与设计文档 §3.2 一致；重复插入同一 `(project_id, user_id)` 应被唯一索引拒绝。

### 步骤 2.3 — 模板表
- **指令**：编写迁移脚本创建 `t_entity_template`（含 `field_schema` JSON、`project_id`、`icon`、`color`）与 `t_relation_template`（含 `directed`、`line_style` JSON、`allowed_from`/`allowed_to` JSON）。
- **验证**：迁移成功；能向 `field_schema`、`line_style` 等 JSON 列写入并读回合法 JSON；非法 JSON 写入被数据库拒绝。

### 步骤 2.4 — 实体表（单父树核心）
- **指令**：编写迁移脚本创建 `t_entity`，含 `parent_id`(NULL 表示根，外键指向自身 `id`)、`template_id`、`status`(RECOMMENDED/DEPRECATED/SIMULATING/空)、`is_milestone`、`remark`、`attributes` JSON；建立 `(project_id, parent_id)` 索引。
- **验证**：迁移成功；能插入 `parent_id IS NULL` 的根节点与引用父节点的子节点。（注：按决策 9 不建物理外键，`parent_id` 合法性由 Service 层校验，见步骤 6.1，不在建表层约束。）

### 步骤 2.5 — 关系、产出物、日志表
- **指令**：编写迁移脚本创建 `t_relation`（`from_entity_id`/`to_entity_id`/`template_id`/`remark`）、`t_asset`（`entity_id`/`asset_type`/`file_name`/`object_key`/`content_text`/`size`/`mime_type`）、`t_operation_log`（`action`/`target_type`/`target_id`/`detail` JSON）。
- **验证**：迁移全部成功；连库确认三张表结构与设计文档 §3.2 一致；从零重建数据库（drop 后重跑全部迁移）能一次性成功。

### 步骤 2.6 — MyBatis-Plus 实体与 Mapper 骨架
- **指令**：为上述每张表创建对应 Entity 类与基础 Mapper，配置 MyBatis-Plus（主键策略、逻辑删除字段 `deleted`、自动填充 `created_at`/`updated_at`/`created_by`）。
- **验证**：编写针对每个 Mapper 的最小集成测试，对每张表做一次插入+查询，断言逻辑删除生效（软删后默认查询不返回）、审计字段被自动填充。测试全部通过。

---

## 阶段三：认证与鉴权

### 步骤 3.1 — 密码加密与用户注册
- **指令**：实现用户注册，密码用 BCrypt 加密后存 `password_hash`；`username` 唯一；注册接口 `POST /api/auth/register` 公开。
- **验证**：注册对所有人公开（决策 5）；注册后数据库存储的是 BCrypt 哈希而非明文；重复 `username` 注册返回明确业务错误；测试覆盖成功注册与重名冲突两条路径。

### 步骤 3.2 — 登录签发 JWT
- **指令**：实现 `POST /api/auth/login`（公开），校验密码后签发含 `userId`、`username`、**签发时间 iat**（登出比对用，见决策 6）的 JWT；配置签名密钥与过期时间。
- **验证**：正确凭据返回可解析的 JWT；错误密码/不存在用户返回统一鉴权失败错误；测试断言返回的 JWT 载荷含 `userId`、`username`、`iat`。

### 步骤 3.3 — JWT 认证过滤器
- **指令**：实现 `JwtAuthenticationFilter` 解析 `Authorization: Bearer <JWT>`，填充 Spring Security 上下文；校验 token 签发时间 iat 是否早于该用户 `token_invalid_before`（早于则判为已登出/失效，见决策 6）；配置公开路径白名单（注册/登录/Swagger）与其余需认证。
- **验证**：带合法 JWT 访问受保护接口通过；无 token 或过期/伪造 token 被拒（401）；公开路径无 token 可访问；iat 早于 `token_invalid_before` 的 token 被拒。测试覆盖这五种情形。

### 步骤 3.4 — 当前用户与登出
- **指令**：实现 `GET /api/auth/me`（返回当前用户信息）与 `POST /api/auth/logout`（将当前用户 `token_invalid_before` 置为当前时间，使其已签发的 token 全部失效，见决策 6）。
- **验证**：`/api/auth/me` 携带合法 JWT 返回对应用户；未认证访问被拒；调用 logout 后，登出前签发的 token 再访问受保护接口被拒（401）。测试通过。

### 步骤 3.5 — 项目级角色注解与切面
- **指令**：实现自定义注解 `@RequireProjectRole(角色)` 与其切面：从路径 `{pid}` 或请求体解析 `projectId`；若当前用户 `system_role = SUPER_ADMIN` 直接放行，否则查 `t_project_member` 校验其角色 ≥ 要求角色（角色序：VIEWER < EDITOR < ADMIN）。
- **验证**：编写切面单元/集成测试覆盖：SuperAdmin 放行；项目成员角色达标放行；角色不足拒绝（403）；非项目成员拒绝。四条路径全部通过。

---

## 阶段四：多项目与成员管理

### 步骤 4.1 — 项目 CRUD
- **指令**：实现 `GET /api/projects`（我参与的项目列表，含我的角色）、`POST /api/projects`（创建者自动成为该项目 ADMIN 成员）、`PUT /api/projects/{id}`（Admin）、`DELETE /api/projects/{id}`（仅 owner）。
- **验证**：创建项目后创建者在 `t_project_member` 中角色为 ADMIN；项目列表只返回当前用户参与的项目且带角色；非 owner 删除被拒；测试覆盖以上路径。

### 步骤 4.2 — 成员管理
- **指令**：实现 `GET /api/projects/{id}/members`（成员可见）、`POST /api/projects/{id}/members`（Admin 邀请/分配角色）、`DELETE /api/projects/{id}/members/{uid}`（Admin 移除）。
- **验证**：Admin 可增删成员并分配角色；Editor/Viewer 调用写操作被拒；重复添加同一成员被唯一索引/业务校验拒绝。测试通过。

### 步骤 4.3 — 项目数据隔离回归
- **指令**：确认所有项目相关查询都以 `project_id` 为约束，用户无法读取未参与项目的数据。
- **验证**：编写跨项目越权测试：用户 A 用合法 JWT 访问自己未参与的项目 B 的资源，全部被拒（403/404）。测试通过。

---

## 阶段五：模板管理（内容管理页）

### 步骤 5.1 — 实体模板 CRUD
- **指令**：实现 `/api/projects/{pid}/entity-templates` 的增删改查（读:成员，写:Admin）。写入/校验 `field_schema` JSON（字段含 key/label/type，type ∈ TEXT/NUMBER/ENUM/DATE/FILE）。删除前校验是否被 `t_entity` 引用，被引用则拒绝并告警。
- **验证**：Admin 可增删改；成员可查；`field_schema` 结构非法被拒；删除被实体引用的模板被拒。测试覆盖各路径。

### 步骤 5.2 — 关系模板 CRUD
- **指令**：实现 `/api/projects/{pid}/relation-templates` 增删改查（读:成员，写:Admin），含 `directed`、`line_style`、`allowed_from`/`allowed_to`（允许为空=不限）。删除前校验是否被 `t_relation` 引用。
- **验证**：CRUD 与权限校验通过；`allowed_from`/`allowed_to` 为空表示不限；删除被引用模板被拒。测试通过。

---

## 阶段六：实体 CRUD 与树查询

### 步骤 6.1 — 实体创建与属性校验
- **指令**：实现实体创建 `POST /api/projects/{pid}/entities`（写:Editor+）：指定 `template_id`、`parent_id`（可空=根）、`name`、`attributes`。依据模板 `field_schema` 校验 `attributes`（必填字段、类型、ENUM 选项）；type=FILE 的字段其值须为一个 asset id 且该产出物属于当前实体（见决策 10）。`parent_id`、`template_id` 的合法性由 Service 层校验（决策 9，无物理外键）。
- **验证**：合法实体创建成功且 `attributes` 正确入库；缺必填字段或类型不符被拒；`parent_id` 指向非本项目实体被 Service 层拒绝；FILE 字段值指向不存在或不属于本实体的 asset 被拒。测试覆盖各路径。

### 步骤 6.2 — 实体更新与删除策略
- **指令**：实现实体更新（属性/名称/备注）与删除 `DELETE`（写:Editor+），删除接口接受 `childStrategy` 参数：
  - `CASCADE`：递归 CTE 查出子树全部 id，删除其关联 `t_relation`(from/to 命中)、`t_asset`（含对应 MinIO 对象），再软删子树全部实体。
  - `PROMOTE`：仅删除该节点，其直接子节点 `parent_id` 改为被删节点的 `parent_id`（删根则子节点变根，`parent_id=NULL`）；该节点自身关联 relation/asset 一并删除。
- **验证**：构造多层子树，分别用两种策略删除并断言结果：CASCADE 后整棵子树及关联被清；PROMOTE 后仅目标节点消失、子节点正确上提。测试覆盖删根节点的 PROMOTE 边界。

### 步骤 6.3 — 整树查询（递归 CTE）
- **指令**：实现 `GET /api/projects/{pid}/entities/tree`（成员），用 MySQL `WITH RECURSIVE` 从根向下查出全树（含 depth），后端组装为嵌套树结构 VO 返回。
- **验证**：构造已知形状的树，断言返回的嵌套结构层级、父子归属、节点数与构造一致；已软删节点不出现在结果中。测试通过。

### 步骤 6.4 — 路径追溯
- **指令**：实现 `GET /api/entities/{id}/path`（成员），用递归 CTE 从当前节点向上查到根，返回根→当前节点的有序路径。
- **验证**：对树中一个深层节点断言返回路径顺序正确、首元素为根、末元素为该节点。测试通过。

### 步骤 6.5 — 里程碑与状态标记
- **指令**：实现 `PUT /api/entities/{id}/milestone`（切换里程碑）与 `PUT /api/entities/{id}/status`（设置状态标记，RECOMMENDED/DEPRECATED/SIMULATING **互斥单选**或清空）（写:Editor+）。
- **验证**：切换里程碑后 `is_milestone` 正确翻转；设置状态为互斥单选（设新值覆盖旧值，可清空）；非法状态值被拒。测试通过。

---

## 阶段七：语义关系与统计

### 步骤 7.1 — 语义关系 CRUD
- **指令**：实现 `/api/projects/{pid}/relations` 增删改查（读:成员，写:Editor+），存 `from_entity_id`/`to_entity_id`/`template_id`/`remark`。若关系模板配置了 `allowed_from`/`allowed_to`，校验两端实体类型是否被允许。**确认父子迭代关系绝不写入本表。**
- **验证**：合法语义关系创建成功；违反 `allowed_from`/`allowed_to` 约束被拒；两端实体跨项目被拒。测试确认 `t_relation` 中不含任何等价于 parent_id 的父子记录。

### 步骤 7.2 — 统计面板
- **指令**：实现 `GET /api/projects/{pid}/stats`（成员）：返回方案节点总数（=项目内全部实体数，决策 4）、已完成仿真数量（=RECOMMENDED 数 + DEPRECATED 数，决策 3）、仿真中数量（SIMULATING）、推荐方案数量（RECOMMENDED）；并接受一个字段 key 参数（如 `numberFieldKey`），对该指定 NUMBER 属性用 MySQL JSON 提取后求最大值（决策 4）。
- **验证**：构造已知数据集，断言各统计项数值与手工计算一致：全体计数、已完成=推荐+淘汰之和、指定字段的 NUMBER 最大值正确。测试通过。

---

## 阶段八：产出物（MinIO）

### 步骤 8.1 — MinIO 接入
- **指令**：接入 MinIO Java SDK，配置 bucket；封装上传、删除、生成下载（预签名 URL 或流式）能力。
- **验证**：应用启动能连通 MinIO；一个最小集成测试完成对象的上传→下载→删除闭环。测试通过（无 MinIO 环境时明确标注为需环境的测试）。

### 步骤 8.2 — 产出物上传
- **指令**：实现 `POST /api/entities/{id}/assets`（Editor+，`MultipartFile`）：校验文件类型与大小（≤100MB），存 MinIO 得 `object_key`，DB 记录元信息（`asset_type`/`file_name`/`size`/`mime_type`）；TEXT 类型可内联存 `content_text`。
- **验证**：合法文件上传后 MinIO 有对象、DB 有元信息且 `object_key` 对应；超限文件被拒；测试覆盖普通文件与 TEXT 内联两类。

### 步骤 8.3 — 产出物列表 / 下载 / 删除
- **指令**：实现 `GET /api/entities/{id}/assets`（成员，列表）、`GET /api/assets/{id}/download`（成员，下载，初期只下载不预览）、`DELETE /api/assets/{id}`（Editor+，同时删 MinIO 对象）。
- **验证**：列表返回该实体全部产出物；下载得到与上传一致的字节内容；删除后 MinIO 对象与 DB 记录均消失。测试通过。

---

## 阶段九：搜索与高亮（后端）

### 步骤 9.1 — 关键字搜索
- **指令**：实现 `GET /api/projects/{pid}/search?keyword=`（成员），对 `t_entity.name`、`t_entity.remark`、`t_relation.remark` 做 `LIKE` 模糊查询，返回命中列表（含 entityId/relationId 与匹配片段）。
- **验证**：构造含目标关键字的实体与关系，断言命中项被返回、无关项不返回、结果标注了来源类型与 id。测试通过。

---

## 阶段十：补充功能

### 步骤 10.1 — 操作日志（AOP）
- **指令**：用 AOP 切面统一记录写操作到 `t_operation_log`（`user_id`/`action`/`target_type`/`target_id`/`detail` JSON 快照）；实现 `GET /api/projects/{pid}/logs`（Admin）。
- **验证**：执行一次实体创建后，`t_operation_log` 出现对应记录且 `detail` 含快照；Admin 可查日志、非 Admin 被拒。测试通过。

### 步骤 10.2 — 导出项目 JSON
- **指令**：实现 `GET /api/projects/{pid}/export`（Admin），导出项目全部模板/实体/关系/产出物元信息为 JSON。
- **验证**：导出 JSON 包含项目全量数据，结构完整可被后续导入消费。测试通过。

### 步骤 10.3 — 导入项目 JSON（新 id 重映射）
- **指令**：实现 `POST /api/projects/{pid}/import`（Admin）：为导入的所有实体/模板/关系**一律分配新 id**，并按旧 id→新 id 映射表重建 `parent_id`、`template_id`、`from_entity_id`、`to_entity_id` 等引用。
- **验证**：将步骤 10.2 的导出结果导入到另一空项目，断言树结构与引用关系完整还原、所有 id 均为新分配、无 id 冲突。测试通过。

### 步骤 10.4 — 迭代对比与路径追溯（收尾）
- **指令**：确认迭代对比（多节点属性/产出物差异）所需数据接口齐备（可复用实体详情与路径追溯接口），补齐缺口。
- **验证**：给定若干节点 id，能取回其对比所需的全部属性与产出物元信息。测试通过。

---

## 全局验收（阶段十完成后）

- **指令**：从零数据库重跑全部 Flyway 迁移并启动应用；执行全量测试套件；对照设计文档 §5 REST API 表逐条核对接口与权限。
- **验证**：`./mvnw clean test` 全绿；从零重建库+启动无错；§5 中每个接口均存在且权限校验符合 §4.3 角色矩阵。

> 说明：本计划仅覆盖后端（本仓库）。前端（React 18 + Vite + AntD 5 + G6 v5 + Zustand，见设计文档 §6）为独立工程，按各接口就绪节奏另行推进；二期项（在线预览、Elasticsearch、节点坐标持久化、撤销/重做、DAG 多父）不在本计划范围。