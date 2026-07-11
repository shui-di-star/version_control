#!/usr/bin/env bash
# ==============================================================
# VCS 数据备份脚本
# 用法: cd /opt/vcs-release && bash backup.sh
# ==============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

BACKUP_DIR="$SCRIPT_DIR/backups"
mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "===== VCS 数据备份 ====="

# ---- MySQL ----
echo "  备份 MySQL ..."
MYSQL_PWD=$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2)
docker-compose exec -T mysql mysqldump -u root -p"$MYSQL_PWD" --all-databases > "$BACKUP_DIR/mysql_backup_$TIMESTAMP.sql"
echo "  MySQL: $BACKUP_DIR/mysql_backup_$TIMESTAMP.sql ($(du -h "$BACKUP_DIR/mysql_backup_$TIMESTAMP.sql" | cut -f1))"

# ---- MinIO（通过 docker cp 备份数据目录）----
echo "  备份 MinIO 数据 ..."
MINIO_CONTAINER=$(docker-compose ps -q minio 2>/dev/null || true)
if [ -n "$MINIO_CONTAINER" ]; then
    docker cp "$MINIO_CONTAINER:/data" "$BACKUP_DIR/minio_data_$TIMESTAMP"
    echo "  MinIO: $BACKUP_DIR/minio_data_$TIMESTAMP/"
else
    echo "  警告：MinIO 容器未运行，跳过"
fi

# ---- 清理 7 天前的备份 ----
echo "  清理 7 天前的备份 ..."
find "$BACKUP_DIR" -name "mysql_backup_*" -mtime +7 -delete 2>/dev/null || true
find "$BACKUP_DIR" -name "minio_data_*" -maxdepth 1 -mtime +7 -type d -exec rm -rf {} + 2>/dev/null || true

echo ""
echo "===== 备份完成 ====="
echo "  备份目录: $BACKUP_DIR"
