# 架构说明（Architecture）

> 记录代码库的结构与每个文件/目录的作用，供后续开发者快速上手。
> 随开发推进增量更新：每完成一个重大功能或里程碑后，补充新增文件的职责与关键设计洞察。
> 权威技术决策见 `design-document.md` 与 `implementation_plan.md` 的「已确认前置决策」节。

---

## 当前状态

**阶段一 · 步骤 1.6 完成后**：Spring Boot 4.0.7（Java 21）工程已引入全套核心依赖，数据源已配置可连库启动（Tomcat 8080，HikariCP → `vcs_dev`），具备统一响应 `Result<T>` 与全局异常处理，并接入 **SpringDoc/Swagger UI**（`/v3/api-docs`、`/swagger-ui`）。阶段一（骨架与基础设施）至此完成。仍无建表脚本（`db/migration` 待阶段二）、无业务实体/接口、无 SecurityConfig（默认全局 401，待鉴权阶段）。

## 技术基线（已定）

- **构建**：Maven（Maven Wrapper 锁定，JVM 走 `JAVA_HOME` 的 JDK 21）。
- **框架**：Spring Boot 4.0.7 / Spring Framework 7 线。
- **已引入依赖**：`spring-boot-starter-web` / `-validation` / `-security`、`mybatis-plus-spring-boot4-starter` 3.5.16、`mysql-connector-j`、`flyway-core` + `flyway-mysql`、jjwt 0.12.7（api/impl/jackson）、MapStruct 1.6.3（+ lombok-mapstruct-binding）、Lombok、Hutool 5.8.35、**SpringDoc `springdoc-openapi-starter-webmvc-ui` 3.0.3（SB4 线）**。
- **待引入**：MinIO 8.5.14（阶段八，含 SB4 兼容性预案）。详见 `implementation_plan.md` 步骤 1.2 清单。

## 目录与文件职责

### 构建与工程配置
- **`pom.xml`** — Maven 构建定义。父 POM 为 Spring Boot 4.0.7，`<java.version>` 为 21。已引入 web/security/validation、MyBatis-Plus(SB4 变体)、MySQL 驱动、Flyway(core+mysql)、jjwt 三件套、MapStruct、Lombok、Hutool；非 BOM 依赖的版本号集中在 `<properties>`。`build` 段配置了 compiler 插件的 annotation processor 链（lombok → mapstruct-processor → lombok-mapstruct-binding，**顺序不可乱**）与 boot 插件排除 lombok。
- **`mvnw` / `mvnw.cmd`** — Maven Wrapper 脚本（*nix / Windows），保证无需本机装 Maven 即可构建；实际用哪个 JDK 取决于 `JAVA_HOME`。
- **`.mvn/wrapper/maven-wrapper.properties`** — Wrapper 配置，指定所用 Maven 版本（3.9.16）与下载源。
- **`.gitattributes` / `.gitignore`** — Git 属性与忽略规则；`.gitignore` 已排除 `target/`、`.idea`、IDE 缓存、`HELP.md` 等。

### 应用源码
- **`src/main/java/com/example/version_control_system/VersionControlSystemApplication.java`** — Spring Boot 启动类（`@SpringBootApplication` + `main`）。当前工程的唯一业务源文件。
  - 注意：实际包名是 `com.example.version_control_system`（IDE 生成），与 design-document §2.3 里建议的 `com.sim.versionmgr` 不同。以磁盘实际为准，改名需先与用户确认。
- **`src/main/resources/application.properties`** — 应用配置。含数据源（指向 `vcs_dev`，凭据走 `${DB_*}` 环境变量占位、默认 root/root）、Flyway（`classpath:db/migration`）、MyBatis-Plus（驼峰映射、逻辑删除 `deleted`）、上传上限 100MB、JWT（`app.jwt.*`）、MinIO 占位（`app.minio.*`，阶段八启用）。敏感值均支持环境变量覆盖。
- **`src/main/java/.../common/`** — 统一响应层。
  - **`Result<T>`** — 统一响应体 `{code, message, data}`；静态工厂 `success`/`error` 系列。所有 Controller 返回值都应包成 `Result`。
  - **`ResultCode`** — 错误码枚举，分段：0 成功；1000 段客户端/系统；2000 段鉴权；3000 段资源/业务。新增错误码在对应段内追加。
- **`src/main/java/.../exception/`** — 异常层。
  - **`BusinessException`** — 业务异常，携带 `ResultCode`。Service 层抛它来表达可预期的业务错误。
  - **`GlobalExceptionHandler`** — `@RestControllerAdvice` 全局兜底：业务异常→自带码；`@Valid` 校验失败→400 + VALIDATION_ERROR；其他未预期异常→500 + INTERNAL_ERROR（记日志，不泄露堆栈给客户端）。
- **`src/test/java/com/example/version_control_system/VersionControlSystemApplicationTests.java`** — 默认 context 加载测试（`@SpringBootTest` 的空 `contextLoads`）。步骤 1.1 验证即依赖它通过。

### 文档 / 记忆库（`memory-bank/` 与根目录）
- **`PRD.md`**（根目录）— 原始产品需求文档，业务背景来源。
- **`memory-bank/design-document.md`** — 技术设计定稿（架构、数据模型、API、模块划分）。开发权威依据之一。
- **`memory-bank/implementation_plan.md`** — 分阶段实施计划 + 「已确认前置决策」；每步含指令与验证，是执行剧本。
- **`memory-bank/progress.md`** — 开发进度日志，记录每步做了什么、验证结果、环境陷阱。
- **`memory-bank/architecture.md`** — 本文件，代码结构与文件职责说明。
- **`CLAUDE.md`**（根目录）— 给 Claude Code 的仓库指南，浓缩关键命令、架构与决策，并要求写代码前先读 memory-bank。

## 关键洞察（供后续者）

- **JDK 版本陷阱**：`PATH` 的 `java` 是 JDK 17，`JAVA_HOME` 是 JDK 21。构建必须走 21（Maven Wrapper 已保证）。用 `./mvnw -v` 核实。
- **当前启动会因缺数据源而失败**：引入 web + jdbc 自动配置后，未配 `spring.datasource.url` 时应用启动报错，属预期；数据源配置见步骤 1.4。配好后应用将常驻监听 8080。
- **annotation processor 顺序**：Lombok 与 MapStruct 同用时，processor 声明顺序为 lombok → mapstruct-processor → lombok-mapstruct-binding，错序会导致 MapStruct 读不到 Lombok 生成的 getter/setter、映射丢字段。
- **Spring Boot 4 切片测试变更**：`@WebMvcTest`/`@DataJpaTest` 等 slice 注解已移出核心 `spring-boot-test-autoconfigure` jar（仅剩 jdbc/json）。Controller 单测优先用 `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler)`——不启 Spring 上下文/Security/DB，最轻量、最稳。
- **SpringDoc 版本线**：SB4 必须用 **SpringDoc 3.0.x**（2.8.x 是 SB3 线，装上会因 SB4 模块化不兼容）。Maven Central 的搜索索引可能滞后（曾只显示到 2.8.6），版本存在性以直接探 `repo1.maven.org` 的 pom HTTP 200 为准。
- **SpringDoc 端点被 Security 拦截**：无 SecurityConfig 时 Spring Security 默认对所有请求返回 401，含 `/v3/api-docs`、`/swagger-ui/**`。鉴权阶段配置 SecurityConfig 时须对这些路径 permitAll，否则文档页打不开。

## 本地环境须知

- **MySQL**：本机 8.0.45，Windows 服务名 `mysql`（启动类型 Automatic，但常处于 Stopped）。开发前先启动：`powershell Start-Service mysql`（需管理员）。数据库：`vcs_dev`（开发）、`vcs_test`（测试，决策 7），均 utf8mb4。root/root。
- **mysql CLI 不在 PATH**：客户端绝对路径 `D:\software\Mysql\mysql-8.0.45-winx64\bin\mysql`（环境变量 `MYSQL_HOME` 指向安装目录）。
- **应用端口**：8080。
- **分层与红线**：后续代码遵循 `Controller → Service(+impl) → Mapper`，并严守实施计划顶部的设计红线（单父树、仅 MySQL 8、JSON 动态属性、无 Docker、逻辑外键等）。