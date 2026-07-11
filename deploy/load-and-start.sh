#!/usr/bin/env bash
# ==============================================================
# VCS 首次部署脚本（离线服务器）
# 用法: cd /opt/vcs-release && bash load-and-start.sh
# ==============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- 检查 .env ----
if [ ! -f ".env" ]; then
    echo "错误：请先复制并编辑 .env 文件"
    echo "  cp .env.example .env"
    echo "  nano .env"
    exit 1
fi

echo "===== 1. 导入 Docker 镜像 ====="
if [ -f "images.tar" ]; then
    echo "  正在导入镜像（可能需要几分钟）..."
    docker load -i images.tar
    echo "  镜像导入完成"
else
    echo "  未找到 images.tar，跳过镜像导入（需要联网）"
fi

echo "===== 2. 构建应用镜像并启动 ====="
docker-compose up -d --build

echo "===== 3. 等待服务就绪 ====="
echo -n "  等待 MySQL"
for i in $(seq 1 60); do
    if docker-compose exec -T mysql mysqladmin ping -h localhost --silent 2>/dev/null; then
        echo " ✓"
        break
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "========================================="
echo "  部署完成！"
echo ""
echo "  访问地址: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo '服务器IP')"
echo "  MinIO 控制台: http://服务器IP:9001"
echo ""
echo "  常用命令:"
echo "    docker-compose ps          # 查看状态"
echo "    docker-compose logs -f app # 后端日志"
echo "    docker-compose restart app # 重启后端"
echo "========================================="
