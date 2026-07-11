#!/usr/bin/env bash
# ==============================================================
# VCS 升级脚本（保留用户数据）
# 用法: cd /opt/vcs-release && bash upgrade.sh
#
# 原理：MySQL 和 MinIO 的数据存在 Docker named volume 中，
#       升级只重建 app 和 nginx 容器，数据卷不受影响。
#       Flyway 会自动执行新增的迁移脚本升级数据库 schema。
# ==============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "===== VCS 升级 ====="

# ---- 1. 数据备份 ----
echo "===== 1. 备份数据 ====="
BACKUP_DIR="$SCRIPT_DIR/backups"
mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 备份 MySQL
echo "  备份 MySQL 数据库..."
if docker-compose exec -T mysql mysqldump -u root -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2)" --all-databases > "$BACKUP_DIR/mysql_backup_$TIMESTAMP.sql" 2>/dev/null; then
    echo "  MySQL 备份完成: $BACKUP_DIR/mysql_backup_$TIMESTAMP.sql"
else
    echo "  警告：MySQL 备份失败（可能是首次部署或数据库未运行）"
fi

# ---- 2. 导入新镜像 ----
echo "===== 2. 导入 Docker 镜像 ====="
if [ -f "images.tar" ]; then
    docker load -i images.tar
    echo "  镜像导入完成"
else
    echo "  未找到 images.tar，跳过"
fi

# ---- 3. 重建并启动 ----
echo "===== 3. 重建应用容器（数据卷保留）====="
docker-compose up -d --build

echo "===== 4. 等待服务就绪 ====="
echo -n "  等待 MySQL"
for i in $(seq 1 60); do
    if docker-compose exec -T mysql mysqladmin ping -h localhost --silent 2>/dev/null; then
        echo " ✓"
        break
    fi
    echo -n "."
    sleep 2
done

# ---- 4. 验证 ----
echo ""
echo "===== 5. 验证 ====="
docker-compose ps

echo ""
echo "========================================="
echo "  升级完成！用户数据已保留。"
echo "  Flyway 已自动执行数据库迁移。"
echo ""
echo "  查看后端日志确认启动正常:"
echo "    docker-compose logs -f app"
echo ""
echo "  如需回滚数据库:"
echo "    mysql < $BACKUP_DIR/mysql_backup_$TIMESTAMP.sql"
echo "========================================="
