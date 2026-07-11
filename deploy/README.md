# VCS 部署指南

**本机编译打包，服务器只装 Docker，一条命令启动。支持完全离线部署。**

## 架构

```
浏览器 :80 → Nginx ─→ /api/*  → app:8080 (Spring Boot) → MySQL
                    └→ /*     → 前端静态文件              → MinIO
```

| 容器 | 说明 | 数据持久化 |
|------|------|-----------|
| nginx | 前端静态 + 反向代理（:80） | 无状态 |
| app | Spring Boot 后端（内部 :8080） | 无状态 |
| mysql | MySQL 8.0 | `mysql_data` volume |
| minio | 对象存储，:9001 管理控制台 | `minio_data` volume |

## 首次部署

### 1. 本机打包（需要联网 + JDK 21 + Node.js + Docker）

```bash
bash deploy/build.sh
```

产出 `vcs-release.tar.gz`，包含：JAR、前端 dist、Docker 配置、**所有基础镜像**（mysql、minio、nginx、jre）。

### 2. 上传到服务器

```bash
scp vcs-release.tar.gz user@your-server:/opt/
```

### 3. 服务器部署（仅需 Docker，无需联网）

```bash
# 安装 Docker（首次，需要联网或离线安装 Docker）
# curl -fsSL https://get.docker.com | sh

# 解压
cd /opt
tar -xzf vcs-release.tar.gz
cd vcs-release

# 配置密码（必填项见下方 .env 说明）
cp .env.example .env
nano .env

# 一键启动
bash load-and-start.sh
```

访问 `http://服务器IP` 即可使用。

## .env 配置

| 变量 | 说明 | 必填 |
|------|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL 密码 | **是** |
| `JWT_SECRET` | JWT 密钥，用 `openssl rand -base64 48` 生成 | **是** |
| `MINIO_ROOT_PASSWORD` | MinIO 密码 | **是** |
| `MYSQL_DATABASE` | 数据库名（默认 `vcs_prod`） | 否 |
| `MINIO_ROOT_USER` | MinIO 用户名（默认 `minioadmin`） | 否 |
| `MINIO_BUCKET` | 存储桶名（默认 `vcs-assets`） | 否 |

## 升级版本（用户数据不会丢失）

升级只替换应用代码，MySQL 和 MinIO 的数据存在 Docker volume 中，不受影响。

```bash
# 1. 本机重新打包
bash deploy/build.sh

# 2. 上传到服务器
scp vcs-release.tar.gz user@server:/opt/

# 3. 服务器执行升级
cd /opt
tar -xzf vcs-release.tar.gz
cd vcs-release
bash upgrade.sh    # 自动备份数据库 → 导入镜像 → 重建容器
```

`upgrade.sh` 会自动：
1. 备份 MySQL 数据库到 `backups/` 目录
2. 导入新的 Docker 镜像
3. 重建 app 和 nginx 容器（数据卷保留）
4. Flyway 自动执行新增的数据库迁移脚本

### 数据安全原则

- `docker-compose up -d --build` → **安全**，只重建容器，volume 不动
- `docker-compose down` → **安全**，停止容器，volume 保留
- `docker-compose down -v` → **危险！删除所有 volume，用户数据全部丢失**

## 数据备份

```bash
bash backup.sh    # 备份 MySQL + MinIO 数据到 backups/ 目录
```

自动清理 7 天前的旧备份。建议定期执行或配置 crontab：
```bash
# 每天凌晨 3 点自动备份
0 3 * * * cd /opt/vcs-release && bash backup.sh
```

## 常用命令

```bash
docker-compose ps              # 查看状态
docker-compose logs -f app     # 后端日志
docker-compose logs -f mysql   # MySQL 日志
docker-compose restart app     # 重启后端
docker-compose down            # 停止所有（数据保留）
docker-compose down -v         # 停止并删除数据（⚠️ 慎用！）
```
