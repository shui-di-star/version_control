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

---

### 步骤 1.5 — 统一响应 Result<T> 与全局异常处理 ✅（2026-07-02 完成）

**做了什么**（新增 4 个生产类）
- `common/ResultCode.java`：错误码枚举。SUCCESS(0)；客户端类 1000 段（BAD_REQUEST 1000 / VALIDATION_ERROR 1001 / INTERNAL_ERROR 1002）；鉴权类 2000 段（UNAUTHORIZED 2000 / FORBIDDEN 2001）；资源/业务类 3000 段（NOT_FOUND 3000 / CONFLICT 3001 / BUSINESS_ERROR 3002）。每项带 `code` + `message`。
- `common/Result<T>`：统一响应体 `{code, message, data}`。静态工厂 `success(T)` / `success()` / `error(ResultCode)` / `error(ResultCode, String)` / `error(int, String)`。
- `exception/BusinessException`：`RuntimeException`，携带 `ResultCode`；构造器 `(ResultCode)` 与 `(ResultCode, message)`。
- `exception/GlobalExceptionHandler`：`@RestControllerAdvice`。`handleBusiness` 用异常自带码返回；`handleValidation`（`MethodArgumentNotValidException`，HTTP 400）拼接字段错误 → VALIDATION_ERROR；`handleUnexpected`（兜底 Exception，HTTP 500）记 error 日志并返回 INTERNAL_ERROR，**不向客户端泄露堆栈**。

**验证结果**
- 编写临时探针 `ProbeController`（`/api/_probe/ok` 返回 `Result.success("pong")`；`/api/_probe/boom` 抛业务异常）+ `ProbeControllerTest`。
- 两条用例通过：正常路径断言 code=0/message=成功/data=pong；业务异常断言 code=3002/message=探针触发的业务异常/data 不存在。
- **验证后已按计划移除临时探针**（删除 `probe/ProbeController.java`、`probe/ProbeControllerTest.java` 及空目录）。移除后 `./mvnw test` 仅剩默认 context 测试，BUILD SUCCESS。

**关键上下文 / 后续者须知**
- **Spring Boot 4 破坏性变更**：`@WebMvcTest` 等 slice 测试注解已从核心 `spring-boot-test-autoconfigure` jar 中移出（该 jar 现仅剩 jdbc/json slice，无 `web.servlet` 包）。切片测试改用独立模块，或如本步用 **`MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler)`** 独立装配——不加载 Spring 上下文 / Security / DB，最轻量。后续写 Controller 单测优先用 standaloneSetup。
- 探针类为一次性验证工具，已删除；`Result` / `ResultCode` / `BusinessException` / `GlobalExceptionHandler` 为长期生产代码。

**尚未处理（留给步骤 1.6 起）**
- SpringDoc/Swagger UI 集成（1.6，需实测 SB4 兼容版本）。
- `db/migration` 目录与建表脚本（阶段二）。

---

### 步骤 1.6 — 集成 API 文档（SpringDoc/Swagger UI）✅（2026-07-02 完成）

**做了什么**
- `pom.xml` 加入 **`org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.0.3**（版本号入 `<properties>` 的 `springdoc.version`）。无需任何 Java 配置，starter 自动装配 `/v3/api-docs` 与 `/swagger-ui`。

**版本选型（关键 —— 回填步骤 1.2 的待验证项）**
- SpringDoc **2.8.x 是 Spring Boot 3 线**，不能用于 SB4。SB4 对应的是 **SpringDoc 3.0.x**。
- 已核实 `springdoc-openapi:3.0.3` 的父 POM 为 `spring-boot-starter-parent:4.0.5`，且其 starter 依赖引用了 SB4 模块化后的新工件（`spring-boot-tomcat`、`spring-boot-web-server`、`spring-boot-starter-webmvc-test`、`spring-boot-health`）——确认是 SB4/Spring 7 线，与本项目 SB 4.0.7 兼容。
- （注意：Maven Central 的 solr 搜索索引一度只显示到 2.8.6，属索引滞后；直接探 `repo1.maven.org` 确认 3.0.0–3.0.3 的 pom 均 HTTP 200 存在。）

**验证结果**
- `./mvnw clean compile` → BUILD SUCCESS。
- `./mvnw spring-boot:run` 启动，日志出现 `SpringDocAppInitializer`：`/v3/api-docs` 与 `/swagger-ui.html` 默认启用。
- 无凭据访问返回 **401**（Spring Security 默认全局拦截，尚无 SecurityConfig——属预期，放行规则待鉴权阶段）。用日志里的开发生成密码 `user:<pwd>` 认证后：
  - `GET /v3/api-docs` → **200**，返回合法 `openapi:3.1.0` JSON（`paths` 当前为空，因还没有业务 Controller，符合预期）。
  - `GET /swagger-ui/index.html` → **200**，`text/html`，Swagger UI 页面正常。
- 验证后停止应用，端口 8080 已释放。

**关键上下文 / 后续者须知**
- **鉴权阶段须为 SpringDoc 端点放行**：`/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html` 需在 SecurityConfig 里 permitAll（或按环境控制），否则文档页被 401 挡住。
- 生产可用 `springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false` 关闭（日志已提示）。

**尚未处理（留给阶段二）**
- `db/migration` 目录与 Flyway 建表脚本（步骤 2.1 起）。

---

## 阶段二：数据库建表（Flyway）

### 步骤 2.1 — Flyway 基线与迁移目录 ✅（2026-07-02 完成）

**做了什么**
- 建目录 `src/main/resources/db/migration/`，加 `README.md` 明确版本化脚本命名规范 `V<n>__<desc>.sql`（从 V1 起，双下划线分隔）与建表约定（见 design-document §3.2）。
- **改依赖**（`pom.xml`）：把裸 `flyway-core` 换成 **`spring-boot-starter-flyway`**（保留 `flyway-mysql`，`flyway-core` 由其传递引入）。
- **改配置**：修正数据源 URL 的 `characterEncoding=utf8mb4` → **`UTF-8`**。

**两个关键坑（SB4 相关，后续者务必知道）**
1. **Flyway 自动配置在 SB4 被拆成独立模块 `spring-boot-flyway`**：SB3 时代 `FlywayAutoConfiguration` 在核心 `spring-boot-autoconfigure` jar 里，光加 `flyway-core` 就能自动迁移；SB4 把它挪到了 `spring-boot-flyway` 模块。**只加 `flyway-core` 会静默不生效**——应用照常启动、零 Flyway 日志、不建任何表（本步初次实测正是这个现象）。解法：用 `spring-boot-starter-flyway`（它带 `spring-boot-flyway` + jdbc），业务库脚本用的 `flyway-mysql`/`flyway-core` 仍显式声明以锁版本。这与 1.6 发现的 SB4「自动配置模块化」是同一模式（web→spring-boot-web-server、tomcat→spring-boot-tomcat）。
2. **`characterEncoding` 必须是 Java charset 名 `UTF-8`，不是 MySQL 排序集名 `utf8mb4`**：之前 1.4/1.6 用 `utf8mb4` 没报错，是因为 HikariCP 懒开连接、启动时没人真正取连接；**Flyway 是第一个在启动期主动开连接的组件**，才暴露 `Unsupported character encoding 'utf8mb4'`。Connector/J 8.x 用 `UTF-8` 即会在服务端协商 utf8mb4，无需写 `utf8mb4`。

**验证结果**
- `./mvnw spring-boot:run`：Flyway 日志出现 `Database: ... (MySQL 8.0)` → `Creating Schema History table vcs_dev.flyway_schema_history` → `Successfully validated 0 migrations` → `Schema up to date. No migration necessary`；应用正常启动。
- 连库 `SHOW TABLES` 确认 `flyway_schema_history` 已创建（history 行为空，因当前无迁移脚本，符合预期）。
- `./mvnw test` → BUILD SUCCESS（context 测试现也会跑 Flyway）。
- 验证后停应用，端口释放。

**关键上下文 / 后续者须知**
- 空迁移目录 + `baseline-on-migrate=true`：Flyway 日志会说 "All configured schemas are empty; baseline operation skipped"——**空库不会写 baseline 行**，只建 history 表。等有了 V1 脚本，首次迁移才会真正记录。
- 命名规范与建表约定见 `db/migration/README.md`。

**尚未处理（留给步骤 2.2 起）**
- 编写 V1 起的建表脚本：用户/项目表（2.2）、模板表（2.3）、实体表（2.4）、关系/产出物/日志表（2.5）。
