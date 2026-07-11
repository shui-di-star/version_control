#!/usr/bin/env bash
# ==============================================================
# VCS 本机打包脚本（Windows Git Bash / Linux / macOS）
# 前置：本机需要 JDK 21、Node.js、Docker
# 产出：vcs-release.tar.gz（上传到服务器离线部署）
# ==============================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/vcs-release"

echo "===== 1. 构建后端 JAR ====="
cd "$PROJECT_ROOT"
./mvnw clean package -DskipTests
JAR_FILE="target/version_control_system-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "错误：未找到 $JAR_FILE"
    exit 1
fi
echo "  JAR: $JAR_FILE"

echo "===== 2. 构建前端 ====="
cd "$PROJECT_ROOT/frontend"
npm install
npm run build
echo "  前端产出: frontend/dist/"

echo "===== 3. 组装发布包 ====="
cd "$PROJECT_ROOT"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/deploy"

# 后端 JAR
cp "$JAR_FILE" "$BUILD_DIR/app.jar"

# 前端 dist
cp -r frontend/dist "$BUILD_DIR/dist"

# Docker 相关文件
cp Dockerfile "$BUILD_DIR/Dockerfile"
cp docker-compose.yml "$BUILD_DIR/docker-compose.yml"
cp deploy/Dockerfile.nginx "$BUILD_DIR/Dockerfile.nginx"
cp deploy/docker-nginx.conf "$BUILD_DIR/docker-nginx.conf"
cp deploy/.env.example "$BUILD_DIR/.env.example"
cp deploy/load-and-start.sh "$BUILD_DIR/load-and-start.sh"
cp deploy/upgrade.sh "$BUILD_DIR/upgrade.sh"
cp deploy/backup.sh "$BUILD_DIR/backup.sh"
chmod +x "$BUILD_DIR/load-and-start.sh" "$BUILD_DIR/upgrade.sh" "$BUILD_DIR/backup.sh" 2>/dev/null || true

echo "===== 4. 拉取并导出 Docker 基础镜像（离线部署用）====="
IMAGES=(
    "mysql:8.0"
    "minio/minio:latest"
    "nginx:alpine"
    "eclipse-temurin:21-jre-jammy"
)

for img in "${IMAGES[@]}"; do
    echo "  拉取 $img ..."
    docker pull "$img"
done

echo "  导出镜像到 images.tar ..."
docker save "${IMAGES[@]}" -o "$BUILD_DIR/images.tar"
echo "  镜像导出完成 ($(du -h "$BUILD_DIR/images.tar" | cut -f1))"

echo "===== 5. 打包 tar.gz ====="
tar -czf vcs-release.tar.gz -C "$(dirname "$BUILD_DIR")" "$(basename "$BUILD_DIR")"
rm -rf "$BUILD_DIR"

SIZE=$(du -h vcs-release.tar.gz | cut -f1)
echo ""
echo "========================================="
echo "  打包完成！"
echo "  文件: vcs-release.tar.gz ($SIZE)"
echo ""
echo "  部署步骤:"
echo "  1. scp vcs-release.tar.gz user@server:/opt/"
echo "  2. ssh 到服务器:"
echo "     cd /opt && tar -xzf vcs-release.tar.gz && cd vcs-release"
echo "     cp .env.example .env && nano .env  # 填密码"
echo "     bash load-and-start.sh             # 首次部署"
echo ""
echo "  升级步骤:"
echo "     bash upgrade.sh                    # 自动备份 + 升级"
echo "========================================="
