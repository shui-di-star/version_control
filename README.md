# 仿真版本管理系统 (Simulation Version Management)

面向仿真/结构工程师团队的全栈 Web 应用，用于管理仿真方案的迭代关系（树形结构）与产出物。

## 功能特性

- **树形迭代管理** — 单父树结构管理仿真方案的迭代演进，支持无限层级
- **可视化图谱** — AntV G6 v5 驱动的交互式树图，自动布局，节点卡片展示关键指标
- **动态属性模板** — 自定义实体类型及字段（文本/数字/枚举/日期/图片），灵活适配不同仿真场景
- **产出物管理** — MinIO 对象存储，支持文件上传/下载/图片内联预览
- **多项目与权限** — 项目级隔离，三级角色（Admin/Editor/Viewer）+ 全局超管
- **关键字搜索** — 支持搜索卡片属性、实体备注、连线备注，时间范围过滤
- **迭代对比** — 并排对比两个方案节点的属性差异
- **统计面板** — 方案数、完成率、数值字段聚合（最大/最小值）
- **导入导出** — 项目数据 JSON 导出/导入，完全数据隔离
- **操作日志** — AOP 自动记录关键操作

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.0.7 (Java 21) |
| 持久层 | MyBatis-Plus + MySQL 8.0 |
| 认证 | Spring Security + JWT |
| 文件存储 | MinIO |
| 前端框架 | React 18 + TypeScript + Vite |
| UI 组件 | Ant Design 5 |
| 图表可视化 | AntV G6 v5 |
| 状态管理 | Zustand |
| 部署 | Docker Compose (Nginx + App + MySQL + MinIO) |

## 项目结构

```
├── src/main/java/         # 后端源码（Controller → Service → Mapper）
├── src/main/resources/
│   └── db/migration/      # Flyway 数据库迁移脚本
├── frontend/              # React 前端
│   ├── src/
│   │   ├── api/           # 接口封装
│   │   ├── components/    # 组件（TreeGraph, DetailPanel, etc.）
│   │   ├── pages/         # 页面
│   │   ├── stores/        # Zustand 状态
│   │   └── types/         # TypeScript 类型定义
│   └── dist/              # 构建产出
├── deploy/                # 部署脚本与配置
├── docker-compose.yml     # Docker 编排
└── Dockerfile             # 后端镜像
```

## 快速开始

### 本地开发

**前置条件**：JDK 21、Node.js 18+、MySQL 8.0、MinIO

```bash
# 后端
./mvnw spring-boot:run

# 前端（另一终端）
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173，API 代理到 localhost:8080。

### Docker 部署（生产）

```bash
# 1. 本机打包（需要 JDK 21 + Node.js + Docker）
bash deploy/build.sh

# 2. 上传到服务器
scp vcs-release.tar.gz user@server:/opt/

# 3. 服务器部署（仅需 Docker，支持完全离线）
cd /opt && tar -xzf vcs-release.tar.gz && cd vcs-release
cp .env.example .env && nano .env   # 配置密码
bash load-and-start.sh              # 一键启动
```

部署后访问 `http://服务器IP` 即可使用。

### 端口占用

| 端口 | 服务 |
|------|------|
| 80 | Nginx（前端 + API 反代） |
| 3306 | MySQL |
| 9000 | MinIO API |
| 9001 | MinIO 管理控制台 |

## 常用命令

```bash
# 后端
./mvnw clean package            # 构建 JAR
./mvnw test                     # 运行测试

# 前端
cd frontend && npm run build    # 生产构建

# Docker
docker-compose ps               # 查看状态
docker-compose logs -f app      # 后端日志
docker-compose restart app      # 重启后端
bash deploy/backup.sh           # 备份数据
bash deploy/upgrade.sh          # 升级版本
```

## 许可证

MIT
