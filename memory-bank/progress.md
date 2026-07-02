# 开发进度（Progress Log）

> 供后续开发者了解已完成的工作与关键上下文。按时间倒序或阶段顺序记录。
> 实施计划见 `implementation_plan.md`；每完成一步在此追加记录。

---

## 阶段一：项目骨架与基础设施

### 步骤 1.1 — 确认构建与运行基线 ✅（2026-07-02 完成）

**做了什么**
- 核对运行环境：Maven Wrapper（`mvnw`）使用的 JVM 为 **Java 21.0.10**（来自 `JAVA_HOME=D:\software\jdk21`），与 `pom.xml` 的 `<java.version>21</java.version>` 一致。
- 运行 `./mvnw test`：`VersionControlSystemApplicationTests` 1 个测试通过，BUILD SUCCESS。
- 运行 `./mvnw spring-boot:run`：应用启动，Spring context 干净加载（约 0.35s），日志无报错，BUILD SUCCESS。

**验证结果**：通过。构建基线可用。

**关键上下文 / 后续者须知**
- **环境陷阱**：本机 `PATH` 里的 `java` 命令指向 **JDK 17**（`D:\software\jdk17`），但 `JAVA_HOME` 指向 **JDK 21**。Maven Wrapper 优先用 `JAVA_HOME`，所以构建走的是 Java 21，正确。若直接用 IDE 或裸 `java` 命令跑，注意别误用到 17。确认命令：`./mvnw -v` 看 "Java version"。
- **应用启动后立即退出属正常**：当前 `pom.xml` 只有 `spring-boot-starter`（非 web），无内嵌 Tomcat，故没有常驻进程、不监听端口。计划步骤 1.1 验证项里"监听端口"要等**步骤 1.3 引入 web starter 后**才成立。当前阶段只需 context 能加载即达标。
- 首次 `spring-boot:run` 会下载 spring-boot-maven-plugin 相关依赖（jdom2、httpcore5 等），属正常，后续从本地仓库读取。
- Mockito 自附着 agent、CDS sharing 等 WARNING 为 JDK 21 + 现有测试栈的已知噪音，不影响构建，可忽略。

**尚未处理（留给后续步骤）**
- 步骤 1.2：与用户确认依赖版本清单（Spring Boot 4.0.7 已定，见前置决策 1）。
- 尚无 web starter、数据库、任何业务代码。`src/main/resources/application.properties` 仅有 `spring.application.name`。

---

### 步骤 1.2 — 校准依赖版本策略 ✅（2026-07-02 完成）

**做了什么**
- 核实 Spring Boot 4.0.7 兼容线上各依赖的实际可用版本，产出「依赖-版本」清单并经用户确认。清单已落入 `implementation_plan.md` 步骤 1.2 下的引用块。
- 策略：**能被 Spring Boot 4.0.7 BOM 托管的不写死版本**（跟随 BOM），仅对非 BOM 依赖显式指定版本。

**关键结论 / 后续者须知**
- MyBatis-Plus 必须用 **`mybatis-plus-spring-boot4-starter` 3.5.16**（SB4 专用变体），不是普通 `mybatis-plus-boot-starter`。
- Flyway 8.x 起 MySQL 支持被拆分，**必须额外加 `flyway-mysql` 模块**，否则报 "Unsupported Database: MySQL"。
- jjwt 用拆分三件套 **0.12.7**：`jjwt-api` + `jjwt-impl`(runtime) + `jjwt-jackson`(runtime)。
- MapStruct **1.6.3** 与 Lombok 同用，annotation processor 需加 **`lombok-mapstruct-binding` 0.2.0**，否则生成的映射会丢字段。
- **两个待验证项**（本步未引入，避免过早引入不确定性）：
  - **SpringDoc**：对 SB4 支持较新，版本待**步骤 1.6** 实测确认。
  - **MinIO 8.5.14**：依赖 OkHttp，与 SB4 可能冲突，待**阶段八**实测；冲突则改用 S3 兼容 SDK（用户已同意此预案）。

**验证结果**：清单获用户确认；`dependency:tree` 无冲突（见步骤 1.3 一并验证）。

---

### 步骤 1.3 — 引入核心依赖 ✅（2026-07-02 完成）

**做了什么**（改动 `pom.xml`）
- 加入依赖：`spring-boot-starter-web`、`-validation`、`-security`、`mybatis-plus-spring-boot4-starter`、`mysql-connector-j`(runtime)、`flyway-core` + `flyway-mysql`、jjwt 三件套、`mapstruct`、`lombok`(optional)、`hutool-all`；测试加 `spring-security-test`。
- **未引入** SpringDoc、MinIO（分别留待 1.6、阶段八，见上）。
- `build` 配置 `maven-compiler-plugin` 的 `annotationProcessorPaths`：lombok → mapstruct-processor → lombok-mapstruct-binding（顺序有讲究）；`spring-boot-maven-plugin` 排除 lombok 出最终 jar。
- 版本号集中放入 `<properties>`。

**验证结果**
- `./mvnw clean compile` → BUILD SUCCESS。
- `./mvnw dependency:tree` → 无版本冲突告警；slf4j 单版本 2.0.18；未出现 okhttp/kotlin（MinIO 未引入，符合预期）。
- **达标口径**：本步验证核心是「依赖正确引入 + 可编译 + 依赖树干净」，已全部通过。

**关键上下文 / 后续者须知**
- 引入 web + jdbc 自动配置后，**应用当前会因未配数据源而启动失败**（`Failed to configure a DataSource: 'url' attribute is not specified`）。这是**预期**的——数据源配置属于**步骤 1.4**。"完整启动"验证已明确顺延到 1.4 配好数据源后进行（经用户确认）。
- 从此应用**已具备 web 容器（内嵌 Tomcat）**，1.4 配好数据源后启动将常驻监听端口（默认 8080）。

**尚未处理（留给步骤 1.4）**
- 配置数据源（MySQL 连接）、MinIO、JWT 密钥/过期、上传大小上限；敏感值走环境变量占位。配好后验证应用完整启动。

---

### 步骤 1.4 — 配置文件与环境分离 ✅（2026-07-02 完成）

**做了什么**
- **建库**：本机 MySQL 8.0.45 上创建开发库 **`vcs_dev`** 与测试库 **`vcs_test`**（均 utf8mb4 / utf8mb4_0900_ai_ci）。测试库对应决策 7。
- 重写 `src/main/resources/application.properties`：
  - 数据源指向 `vcs_dev`，凭据/URL 走环境变量占位并带本地默认值：`${DB_URL:...}` / `${DB_USERNAME:root}` / `${DB_PASSWORD:root}`。
  - Flyway：启用，`locations=classpath:db/migration`（目录待阶段二建脚本），`baseline-on-migrate=true`。
  - MyBatis-Plus：下划线转驼峰、逻辑删除字段 `deleted`（1=删/0=正常）。
  - 上传上限：单文件 & 单请求 100MB。
  - JWT：`app.jwt.secret`（默认值仅开发用，生产必须用 `JWT_SECRET` 覆盖）、`app.jwt.expiration-ms` 默认 1 天。
  - MinIO：`app.minio.*` 占位（阶段八才被读取，当前 SDK 未引入）。

**验证结果**
- `./mvnw spring-boot:run` → **Tomcat 监听 8080**，`Started ... in 0.971s`，HikariCP 连上 `vcs_dev`，Flyway 空迁移目录正常通过，无报错。
- 缺配置的清晰报错行为在步骤 1.3 已验证（未配 url 时 Spring Boot 报 `Failed to configure a DataSource`）。
- （日志末尾的 `exit code 143` 是 `timeout` 命令到点 SIGTERM 主动杀进程所致，非应用错误。）

**关键上下文 / 后续者须知**
- **MySQL 服务默认是 Stopped**：Windows 服务名为 `mysql`（启动类型 Automatic 但当时未运行）。开发前需先启动：`powershell Start-Service mysql`（需管理员）。
- **mysql CLI 不在 PATH**：客户端在 `D:\software\Mysql\mysql-8.0.45-winx64\bin\mysql`（环境变量 `MYSQL_HOME` 指向此安装目录，但 `%MYSQL_HOME%\bin` 未生效展开）。用绝对路径调用。
- 凭据（root/root）通过默认值内置于配置，**本地零环境变量即可启动**；生产/共享环境用 `DB_*`、`JWT_SECRET` 等环境变量覆盖。
- 配置格式仍用 `.properties`（设计文档 §偏好 yml，但本步未强制切换，保持最小变更）。

**尚未处理（留给步骤 1.5 起）**
- 统一响应 `Result<T>` 与全局异常处理（1.5）、Swagger（1.6）。
- `db/migration` 目录与建表脚本（阶段二）。
