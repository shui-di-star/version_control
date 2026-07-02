仿真版本管理软件 PRD（产品需求文档）


一、产品概述

1.1 产品定位

一款面向仿真工程的版本管理软件，用于管理仿真记录的迭代关系及对应的产出物。通过树状结构（自上而下）的可视化界面，帮助用户清晰追踪不同仿真实体之间的迭代演变，并支持多类型产出物（PPT、文档、表格、静态图片、动态图片、文字等）的统一管理。

1.2 核心价值

可追溯性：清晰记录仿真迭代的来龙去脉
可视化：自上而下的树状迭代关系图，直观易懂
可扩展性：用户自定义实体类型与关系类型
可检索性：基于备注内容的关键字快速定位
可协作性：支持多用户登录与权限管理

1.3 目标用户

仿真工程师
结构工程师
需要管理多版本实验记录的研发团队


二、技术栈与软件框架推荐

后端采用 Java（Spring Boot） 技术栈，前端采用 React 生态。整体选型兼顾企业级稳定性与大模型 Vibe Coding 友好度。
2.1 整体技术选型

层级	技术选型	推荐理由
前端框架	React 18 + TypeScript	生态最成熟，大模型语料丰富
构建工具	Vite	启动快，配置简单
UI 组件库	Ant Design 5	组件齐全，开箱即用，适合管理后台类页面
树图可视化	AntV G6（树图布局）或 React Flow + dagre	原生支持自上而下树状布局、展开折叠、高亮定位
前端状态管理	Zustand / Redux Toolkit	轻量，适合 Vibe Coding
后端框架	Spring Boot 4.x（Java 21）	企业级标准，生态成熟，稳定可靠
持久层框架	MyBatis-Plus 或 Spring Data JPA	MyBatis-Plus 更灵活、CRUD 简洁；JPA 更面向对象
数据库	PostgreSQL（多人）/ MySQL（我电脑安装的版本是8.0.45）	关系型数据库即可满足树状结构，无需图数据库
数据库迁移	Flyway / Liquibase	版本化管理数据库结构
认证鉴权	Spring Security + JWT	标准方案，支持多用户登录与权限控制
API 文档	SpringDoc (Swagger UI)	自动生成接口文档，便于联调
文件存储	MinIO（对象存储）/ 本地文件系统	存放 PPT、文档、图片、动图等产出物
构建工具	Maven（推荐）/ Gradle	Maven 配置更标准，大模型语料更多
接口校验	Hibernate Validator (JSR-380)	参数校验
工具库	Lombok、MapStruct、Hutool	减少样板代码，提升开发效率
桌面化（可选）	Tauri / Electron（仅前端打包）	若需打包成桌面软件

2.2 关于树图库的关键说明

AntV G6：支持 compactBox、dendrogram 等树状布局，可设置布局方向为自上而下（TB, Top-Bottom），原生支持节点展开/折叠、搜索高亮、自定义节点。推荐使用。
React Flow：自定义节点能力强，交互编辑体验好，配合 dagre 布局库实现自上而下树状布局。


建议：树图展示+折叠+高亮优先用 G6 v5；若对节点内嵌产出物缩略图、复杂交互编辑要求高，可考虑 React Flow + dagre。

2.3 数据库选型说明（重要）


本软件迭代关系为树状结构（自上而下），采用关系型数据库。

树状结构在关系型数据库中的存储方案：
邻接表（Adjacency List）：每个实体记录 parent_id，结构简单，适合入门
闭包表（Closure Table）：适合频繁查询整条迭代路径的场景，查询效率更高


建议：先用邻接表 + 递归查询实现（PostgreSQL 可用 WITH RECURSIVE 递归 CTE，MySQL 8+ 同样支持），后期若性能不足再引入闭包表。

2.4 后端工程结构建议（标准分层）

text
src/main/java/com/sim/versionmgr/
├── VersionMgrApplication.java        # 启动类
├── config/                           # 配置（Security、CORS、MinIO、Swagger等）
├── controller/                       # 控制层（REST API）
├── service/                          # 业务逻辑层
│   └── impl/
├── mapper/ (或 repository/)           # 持久层（MyBatis-Plus Mapper / JPA Repository）
├── entity/ (或 domain/)              # 数据库实体
├── dto/                              # 数据传输对象（请求/响应）
├── vo/                               # 视图对象
├── common/                           # 通用类（统一返回、异常、枚举、工具）
│   ├── result/                       # 统一响应结构 Result<T>
│   ├── exception/                    # 全局异常处理
│   └── enums/                        # 枚举（角色、产出物类型等）
└── security/                         # JWT、用户认证相关
text


三、功能需求详述

3.1 用户登录与权限管理（多人协作）

3.1.1 用户认证

用户注册 / 登录 / 登出
基于 JWT 的会话管理（Spring Security 集成）
密码加密存储（BCryptPasswordEncoder）

3.1.2 权限角色

角色	权限说明
管理员（Admin）	全部权限：用户管理、项目管理、模板管理、数据增删改
编辑者（Editor）	在被授权的项目内增删改实体、关系、产出物
查看者（Viewer）	仅查看被授权项目的内容，不可修改

3.1.3 项目级权限

项目创建者可邀请其他用户加入项目，并分配角色
用户仅能访问自己被授权的项目
后端接口通过 Spring Security 注解（如 @PreAuthorize）或自定义拦截器做权限校验


3.2 多项目管理

创建项目：支持创建多个独立的仿真项目空间，项目间数据隔离
项目列表：展示用户参与的所有项目（含角色标识）
项目切换：在顶部导航栏快速切换当前工作项目
项目编辑/删除：项目创建者/管理员可编辑项目信息或删除项目
项目成员管理：邀请成员、分配/移除角色


3.3 内容管理页面（模板管理）


用于定义系统中可用的"实体类型"和"关系类型"模板。

3.3.1 实体类型模板管理

增加实体类型：定义类型名称、图标、颜色、自定义属性字段
编辑实体类型：修改已有类型定义
删除实体类型：删除前校验是否被实体引用（给出警告）
属性字段配置：每个实体类型可配置自定义字段

3.3.2 关系类型模板管理

增加关系类型：定义关系名称、方向性、线条样式（颜色、虚实线）、可连接的实体类型约束
编辑/删除关系类型：同上，删除需校验引用

3.3.3 字段类型支持

字段类型	说明
文本	单行/多行文本
数字	整数/浮点
枚举	下拉单选/多选
文件	见产出物类型
日期	时间戳


自定义字段以 JSON 形式存储在模板表的字段中（PostgreSQL 用 jsonb 类型，MySQL 用 json 类型）。


3.4 主页面（树状迭代关系可视化）

3.4.1 树状关系展示

以**自上而下（Top-Bottom）**的树状结构展示实体的迭代关系
根节点在上，子节点向下逐层展开
节点根据实体类型显示不同图标/颜色
关系连线根据关系类型显示不同样式（颜色、虚实线）
支持节点展开/折叠子节点（折叠后显示子节点数量徽标）
支持画布缩放、拖拽、自动布局
支持里程碑标记节点的特殊样式（如星标图标）

3.4.2 实体操作

添加实体：选择父节点 → 选择实体类型 → 填写属性 → 上传产出物 → 添加备注
编辑实体：修改属性、备注、产出物
删除实体：删除时处理子节点与关联关系（提示级联影响）
查看实体详情：点击节点弹出详情面板，展示所有属性、产出物预览、备注
实体状态标记：可将某个实体节点标记/取消标记为“推荐、淘汰、仿真中”，主页面展示的时候可以根据这个标签显示高亮，默认是选择“全部”
主页面展示统计面板：包括方案节点(表示一共有几个方案节点)、已完成仿真、推荐方案（表示推荐方案数量）、实体各属性最大值等等

3.4.3 关系操作

添加关系：选择父实体 → 子实体 → 关系类型 → 添加备注
编辑/删除关系
关系备注

3.4.4 产出物管理

支持以下产出物类型的上传、存储、预览与下载：
产出物类型	格式示例	预览方式
PPT	.ppt / .pptx	在线预览（第三方组件）/ 下载
文档	.doc / .docx / .pdf / .md	文档预览 / 下载
表格	.xls / .xlsx / .csv	表格预览 / 下载
静态图片	.png / .jpg / .jpeg	图片放大预览
动态图片	.gif / .mp4	GIF 播放 / 视频播放
文字	纯文本 / Markdown	内联展示

产出物与实体绑定，一个实体可挂载多个产出物
产出物列表管理（增删、重命名、下载）
后端实现：文件上传通过 Spring Boot MultipartFile 接收，存储至 MinIO 或本地，数据库记录文件元信息与访问路径


预览实现建议：图片/视频/文字浏览器原生支持；PPT/Word/Excel 可集成 微软 Office Online 预览，或后端用 Apache POI / LibreOffice 转 PDF 后预览，初期可先实现"下载查看"，后续迭代在线预览。

3.4.5 搜索与高亮

关键字搜索框，搜索实体备注和关系备注内容
命中结果列表展示
选中某条结果，在树图中高亮对应实体/关系，并自动定位（居中/聚焦）
支持模糊匹配、高亮匹配文本
后端实现：初期用数据库 LIKE 模糊查询；若数据量大可后期引入 Elasticsearch

3.4.6 过滤器

按实体类型筛选显示/隐藏对应类型节点
按关系类型筛选显示/隐藏对应类型连线
按是否为里程碑节点筛选
多条件组合过滤


3.5 数据持久化与自动保存

所有实体、关系、备注、节点位置等数据自动持久化到数据库
自动保存机制：用户编辑操作（增删改、节点拖动）后前端防抖处理，自动调用后端接口保存，无需手动点击保存
保存状态提示（如"已保存 / 保存中..."）
防止数据丢失：异常断网/刷新后数据可从后端恢复


3.6 其他补充功能（配套设计）

3.6.1 迭代专属功能

迭代对比：选择若干个仿真实体节点，对比其属性与产出物差异（如两张结果图并排对比）
迭代路径追溯：选中任一节点，高亮显示其从根节点到当前节点的完整迭代路径（后端用递归 CTE 查询父链路）

3.6.2 数据管理

导入/导出：支持项目数据导出（JSON）、导入，方便备份与分享
操作日志记录：谁在何时做了什么操作（可用 AOP 切面统一记录）

3.6.3 视图与交互增强

小地图（Minimap）：大型树图导航
一键展开/折叠全部
撤销/重做操作


四、数据模型设计（参考）


数据库以 PostgreSQL 为例，JSON 字段使用 jsonb 类型（MySQL 使用 json）。


					
User（用户）t_user
  - id (PK), username, password_hash, email

				

说明：树状迭代关系通过 t_entity.parent_id 实现；t_relation 表用于记录实体之间额外的、非父子的语义关系（如"参考自""衍生于"）。若迭代关系完全等价于父子关系，则 Relation 表主要承载关系类型语义与备注。


五、后端接口设计（REST API 示例）


统一返回结构 Result<T> { code, message, data }，统一前缀 /api。

模块	方法	路径	说明
认证	POST	/api/auth/register	用户注册
认证	POST	/api/auth/login	登录获取 JWT
认证	POST	/api/auth/logout	登出
项目	GET	/api/projects	获取我的项目列表
项目	POST	/api/projects	创建项目
项目	PUT	/api/projects/{id}	编辑项目
项目	DELETE	/api/projects/{id}	删除项目
成员	POST	/api/projects/{id}/members	邀请成员/分配角色
模板	GET/POST/PUT/DELETE	/api/projects/{pid}/entity-templates	实体模板增删改查
模板	GET/POST/PUT/DELETE	/api/projects/{pid}/relation-templates	关系模板增删改查
实体	GET	/api/projects/{pid}/entities/tree	获取整棵树（含父子结构）
实体	POST/PUT/DELETE	/api/projects/{pid}/entities	实体增删改
实体	PUT	/api/entities/{id}/milestone	切换里程碑标记
实体	GET	/api/entities/{id}/path	获取从根到该节点的路径
关系	GET/POST/PUT/DELETE	/api/projects/{pid}/relations	关系增删改查
产出物	POST	/api/entities/{id}/assets	上传产出物
产出物	GET	/api/entities/{id}/assets	获取产出物列表
产出物	DELETE	/api/assets/{id}	删除产出物
搜索	GET	/api/projects/{pid}/search?keyword=	搜索备注
导入导出	GET	/api/projects/{pid}/export	导出项目 JSON
导入导出	POST	/api/projects/{pid}/import	导入项目 JSON


六、页面结构（信息架构）

text
应用
├── 登录 / 注册页
├── 顶部导航栏（项目切换、搜索框、用户信息、全局操作）
├── 项目列表页（多项目管理、成员管理）
├── 主页面（树状迭代视图）
│   ├── 树图画布（G6 / React Flow，自上而下布局）
│   ├── 左侧工具栏（添加实体/关系、展开折叠全部、过滤器、布局）
│   ├── 右侧详情面板（实体/关系详情、产出物预览与上传、备注编辑、里程碑标记）
│   ├── 底部状态栏（保存状态提示）/ 小地图
│   └── 搜索结果浮层
├── 内容管理页面（模板管理）
│   ├── 实体类型模板 Tab
│   └── 关系类型模板 Tab
└── 设置页面（项目设置、成员权限、导入导出、操作日志）
text


七、非功能性需求

需求	说明
性能	支持至少 1000+ 节点树图流畅渲染（G6 折叠机制）；后端树查询用递归 CTE 优化
响应式	适配桌面主流分辨率
数据安全	自动保存 + 数据库持久化，防丢失；项目级数据权限隔离
安全性	密码 BCrypt 加密、JWT 鉴权、接口权限校验（Spring Security）
可维护性	标准分层架构（Controller-Service-Mapper），便于增量开发
兼容性	主流 Chrome / Edge 浏览器
文件	限制单个产出物文件大小（如 ≤ 100MB，Spring Boot 配置 max-file-size）
跨域	后端配置 CORS，允许前端域名访问


八、Vibe Coding 开发建议

8.1 推荐开发顺序（迭代式）

第一阶段：搭建项目骨架
后端：Spring Boot 4 + Maven + MyBatis-Plus + PostgreSQL/MySQL，跑通启动 + Swagger
前端：Vite + React + TypeScript + AntD，跑通页面骨架
第二阶段：用户注册登录 + Spring Security + JWT 鉴权
第三阶段：多项目管理 + 项目成员权限
第四阶段：数据库表设计与基础 CRUD（模板、实体、关系），Flyway 管理建表脚本
第五阶段：内容管理页面（模板增删改）
第六阶段：实体树查询接口（递归 CTE）+ 前端 G6 树图渲染（自上而下、展开折叠）
第七阶段：树图交互（增删实体/关系、详情面板、备注、里程碑标记）
第八阶段：产出物上传与预览（MinIO/本地 + 多类型支持）
第九阶段：搜索高亮 + 过滤器
第十阶段：自动保存、迭代对比、导入导出、操作日志等补充功能

