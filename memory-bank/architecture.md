# 架构说明（Architecture）

> 记录代码库的结构与每个文件/目录的作用，供后续开发者快速上手。
> 随开发推进增量更新：每完成一个重大功能或里程碑后，补充新增文件的职责与关键设计洞察。
> 权威技术决策见 `design-document.md` 与 `implementation_plan.md` 的「已确认前置决策」节。

---

## 当前状态

**阶段一 · 步骤 1.3 完成后**：Spring Boot 4.0.7（Java 21）工程已引入全套核心依赖（web、security、validation、MyBatis-Plus、Flyway、jjwt、MapStruct、Lombok、Hutool），可编译、依赖树无冲突。已具备内嵌 web 容器，但**尚未配置数据源**，故当前直接启动会失败（属预期，待步骤 1.4）。仍无业务代码、无建表脚本。

## 技术基线（已定）

- **构建**：Maven（Maven Wrapper 锁定，JVM 走 `JAVA_HOME` 的 JDK 21）。
- **框架**：Spring Boot 4.0.7 / Spring Framework 7 线。
- **已引入依赖**：`spring-boot-starter-web` / `-validation` / `-security`、`mybatis-plus-spring-boot4-starter` 3.5.16、`mysql-connector-j`、`flyway-core` + `flyway-mysql`、jjwt 0.12.7（api/impl/jackson）、MapStruct 1.6.3（+ lombok-mapstruct-binding）、Lombok、Hutool 5.8.35。
- **待引入**：SpringDoc（步骤 1.6 定版）、MinIO 8.5.14（阶段八，含 SB4 兼容性预案）。详见 `implementation_plan.md` 步骤 1.2 清单。

## 目录与文件职责

### 构建与工程配置
- **`pom.xml`** — Maven 构建定义。父 POM 为 Spring Boot 4.0.7，`<java.version>` 为 21。已引入 web/security/validation、MyBatis-Plus(SB4 变体)、MySQL 驱动、Flyway(core+mysql)、jjwt 三件套、MapStruct、Lombok、Hutool；非 BOM 依赖的版本号集中在 `<properties>`。`build` 段配置了 compiler 插件的 annotation processor 链（lombok → mapstruct-processor → lombok-mapstruct-binding，**顺序不可乱**）与 boot 插件排除 lombok。
- **`mvnw` / `mvnw.cmd`** — Maven Wrapper 脚本（*nix / Windows），保证无需本机装 Maven 即可构建；实际用哪个 JDK 取决于 `JAVA_HOME`。
- **`.mvn/wrapper/maven-wrapper.properties`** — Wrapper 配置，指定所用 Maven 版本（3.9.16）与下载源。
- **`.gitattributes` / `.gitignore`** — Git 属性与忽略规则；`.gitignore` 已排除 `target/`、`.idea`、IDE 缓存、`HELP.md` 等。

### 应用源码
- **`src/main/java/com/example/version_control_system/VersionControlSystemApplication.java`** — Spring Boot 启动类（`@SpringBootApplication` + `main`）。当前工程的唯一业务源文件。
  - 注意：实际包名是 `com.example.version_control_system`（IDE 生成），与 design-document §2.3 里建议的 `com.sim.versionmgr` 不同。以磁盘实际为准，改名需先与用户确认。
- **`src/main/resources/application.properties`** — 应用配置。当前仅 `spring.application.name`。计划步骤 1.4 会在此（或迁移到 yml）加入数据库、MinIO、JWT、上传大小等配置。
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
- **分层与红线**：后续代码遵循 `Controller → Service(+impl) → Mapper`，并严守实施计划顶部的设计红线（单父树、仅 MySQL 8、JSON 动态属性、无 Docker、逻辑外键等）。