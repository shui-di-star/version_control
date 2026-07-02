# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

仿真版本管理软件 (Simulation Version Management) — a web app for tracking the iteration relationships (tree-structured) and deliverables of simulation records, for teams of simulation/structural engineers. Backend is Spring Boot (Java 21); a React frontend is planned but not yet in this repo.

**Current state:** fresh Spring Boot skeleton only (`VersionControlSystemApplication` + generated test). None of the planned features, tables, or dependencies exist yet. `PRD.md` (product requirements) and `memory-bank/design-document.md` (finalized technical design) are the authoritative specs — read them before implementing any feature. A step-by-step build plan lives in `memory-bank/implementation_plan.md`.

## Commands

Uses the Maven wrapper (`mvnw` / `mvnw.cmd`), so no local Maven install is required.

```bash
./mvnw spring-boot:run          # run the app
./mvnw clean package            # build jar
./mvnw test                     # run all tests
./mvnw test -Dtest=ClassName#methodName   # run a single test
```

## Key design decisions (authoritative — do not deviate)

These were finalized in requirements discussion. When touching data model, tree queries, or tree rendering, follow these exactly:

- **Single-parent tree**: the iteration tree is defined solely by `t_entity.parent_id` (one entity has exactly one parent; `NULL` = root). `t_relation` carries ONLY extra non-parent-child semantic relations (e.g. "references", "derived from"), shown as additional overlay edges. Do NOT implement DAG/multi-parent logic.
- **MySQL 8.0 only** (target is 8.0.45). Use MySQL `json` type and `WITH RECURSIVE` CTEs directly. Do NOT add PostgreSQL-specific syntax or any DB-compatibility layer, despite the PRD mentioning PostgreSQL.
- **Dynamic attribute templates**: users define entity/relation types and their fields. Field definitions live in template tables (`field_schema` JSON); per-entity values live in `t_entity.attributes` JSON.
- **Deliverable preview**: initially download-only. In-browser PPT/Word/Excel preview is deferred to a later iteration.
- **File storage**: MinIO object storage; the DB stores only metadata + object key.
- **Tree layout**: AntV G6 v5 with `compactBox` top-to-bottom. Node positions are **auto-laid-out and NOT persisted** — the PRD's "drag position auto-save" was cancelled. Do not add node-coordinate persistence.
- **ORM**: MyBatis-Plus.

Additional decisions confirmed during planning (see memory-bank/implementation_plan.md "已确认前置决策" for the full list — that section is the authoritative source):

- **Spring Boot 4.0.7** is the target; pick dependency versions on the Spring Boot 4 / Spring Framework 7 compatibility line.
- **Primary keys** use snowflake IDs (MyBatis-Plus `IdType.ASSIGN_ID`, `BIGINT`) on every table — lets the import feature pre-generate new IDs and rebuild references in one pass.
- **No physical foreign keys**; referential integrity (`parent_id`, `template_id`, relation endpoints, etc.) is enforced in the Service layer. Reason: physical FKs conflict with soft-delete (`deleted`).
- **No Docker.** Integration tests run against a dedicated local MySQL 8 database (e.g. `vcs_test`), rebuilt via Flyway before tests — H2 can't handle `WITH RECURSIVE` + `json`. MinIO runs as its native Windows executable, not a container.
- **Backend logout** uses a `t_user.token_invalid_before` timestamp: logout sets it to now; the JWT filter rejects any token whose `iat` predates it (invalidates all of that user's sessions).
- **Stats**: every entity counts as a "方案节点"; "已完成仿真" = RECOMMENDED + DEPRECATED counts; the max-value stat targets a caller-specified NUMBER field key.
- **FILE-typed attributes** store an asset id pointing to one of the entity's own `t_asset` records.

## Architecture (planned — per memory-bank/design-document.md §2-5)

Layered backend: **Controller → Service (+impl) → Mapper (MyBatis-Plus)**, with DTO/VO separated from Entity. Custom recursive-CTE SQL lives in Mappers. Cross-cutting: Spring Security + JWT, global exception handler, AOP operation logging.

**Two-layer auth model:**
1. Authentication — Spring Security + JWT (`JwtAuthenticationFilter`).
2. Project-level authorization — a custom `@RequireProjectRole(...)` annotation + aspect resolves `projectId` from the path/body and checks the caller's role. `t_user.system_role = SUPER_ADMIN` short-circuits to allow all; otherwise the role comes from `t_project_member.role` (ADMIN / EDITOR / VIEWER). Project data is isolated by `t_project_member`.

**Core data tables** (see memory-bank/design-document.md §3 for full columns): `t_user`, `t_project`, `t_project_member`, `t_entity_template`, `t_relation_template`, `t_entity` (the tree, self-referencing via `parent_id`), `t_relation`, `t_asset`, `t_operation_log`. Flyway migrations under `resources/db/migration/`.

**Entity deletion** takes a `childStrategy` param: `CASCADE` (recursively soft-delete the subtree + its relations/assets) or `PROMOTE` (delete only this node, re-point children's `parent_id` to the deleted node's parent).

**Import** always generates new IDs, remapping `parent_id` / `template_id` / `from_entity_id` / `to_entity_id` via an old→new ID map to avoid collisions.

REST APIs are under `/api`, return `Result<T> { code, message, data }`, and authenticate via `Authorization: Bearer <JWT>`.

## Discrepancies to be aware of

- **Package name**: current code is `com.example.version_control_system`; the design doc uses `com.sim.versionmgr`. The existing package is what's actually on disk — confirm with the user before renaming.
- **Spring Boot version**: `pom.xml` pins Spring Boot **4.0.7** — this is confirmed as the target (the design doc's assumption of 3.x is superseded). Choose all dependency versions on the SB4 / Spring 7 line.
- **Config format**: current file is `application.properties`; the design doc references `application.yml`.

重要提示：
写任何代码前必须完整阅读 memory-bank/architecture.md
写任何代码前必须完整阅读 memory-bank/design-document.md
每完成一个重大功能或里程碑后，必须更新 memory-bank/architecture.md 与 memory-bank/progress.md