#!/usr/bin/env bash
# ==============================================================
# VCS 部署 / 更新脚本
# 用法: bash deploy.sh <tar.gz 路径>
# ==============================================================
set -euo pipefail

APP_DIR="/opt/vcs"
NGINX_CONF_DIR="/etc/nginx/sites-enabled"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---- 参数 ----
ARCHIVE="${1:-}"
if [ -z "$ARCHIVE" ]; then
    echo "用法: bash deploy.sh <vcs-release.tar.gz>"
    exit 1
fi

echo "===== 1. 解压 ====="
TMP_DIR=$(mktemp -d)
tar -xzf "$ARCHIVE" -C "$TMP_DIR"
echo "  解压到 $TMP_DIR"

# 自动定位解压后的目录（tar.gz 内可能有一层目录）
RELEASE_DIR="$TMP_DIR"
if [ -d "$TMP_DIR/vcs-release" ]; then
    RELEASE_DIR="$TMP_DIR/vcs-release"
fi

echo "===== 2. 停止旧进程 ====="
if [ -f "$APP_DIR/stop.sh" ]; then
    bash "$APP_DIR/stop.sh" || true
fi

echo "===== 3. 部署后端 JAR ====="
cp "$RELEASE_DIR"/*.jar "$APP_DIR/app.jar"

echo "===== 4. 部署前端 ====="
rm -rf "$APP_DIR/frontend"
cp -r "$RELEASE_DIR/dist" "$APP_DIR/frontend"

echo "===== 5. 更新部署脚本 ====="
for f in start.sh stop.sh deploy.sh env.conf nginx-vcs.conf; do
    if [ -f "$RELEASE_DIR/deploy/$f" ]; then
        cp "$RELEASE_DIR/deploy/$f" "$APP_DIR/$f"
    fi
done
chmod +x "$APP_DIR/start.sh" "$APP_DIR/stop.sh" 2>/dev/null || true

# env.conf 只在首次部署时复制（不覆盖已有配置）
if [ ! -f "$APP_DIR/env.conf" ] && [ -f "$RELEASE_DIR/deploy/env.conf" ]; then
    cp "$RELEASE_DIR/deploy/env.conf" "$APP_DIR/env.conf"
    echo "  已复制 env.conf 模板，请编辑 $APP_DIR/env.conf 填入实际凭据"
fi

echo "===== 6. 更新 Nginx 配置 ====="
cp "$RELEASE_DIR/deploy/nginx-vcs.conf" /etc/nginx/sites-available/vcs.conf
ln -sf /etc/nginx/sites-available/vcs.conf "$NGINX_CONF_DIR/vcs.conf"
# 移除默认站点（可选）
rm -f "$NGINX_CONF_DIR/default" 2>/dev/null || true
nginx -t && systemctl reload nginx
echo "  Nginx 已 reload"

echo "===== 7. 启动后端 ====="
bash "$APP_DIR/start.sh"

echo "===== 8. 清理 ====="
rm -rf "$TMP_DIR"

echo ""
echo "===== 部署完成 ====="
echo "  后端日志: tail -f $APP_DIR/logs/app.log"
echo "  访问地址: http://YOUR_DOMAIN.com"
