#!/usr/bin/env bash
# ==============================================================
# VCS 服务器一次性初始化脚本（Ubuntu / Debian）
# 以 root 或 sudo 执行
# ==============================================================
set -euo pipefail

echo "===== 1. 更新系统包 ====="
apt-get update -y

echo "===== 2. 安装 JDK 21 ====="
apt-get install -y openjdk-21-jdk-headless
java -version

echo "===== 3. 安装 MySQL 8 ====="
apt-get install -y mysql-server
systemctl enable mysql
systemctl start mysql

echo "  创建数据库 vcs_prod ..."
mysql -u root <<'SQL'
CREATE DATABASE IF NOT EXISTS vcs_prod
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
SQL
echo "  数据库 vcs_prod 已就绪"

echo "===== 4. 安装 Nginx ====="
apt-get install -y nginx
systemctl enable nginx

echo "===== 5. 安装 MinIO ====="
MINIO_BIN="/usr/local/bin/minio"
if [ ! -f "$MINIO_BIN" ]; then
    echo "  下载 MinIO ..."
    wget -q https://dl.min.io/server/minio/release/linux-amd64/minio -O "$MINIO_BIN"
    chmod +x "$MINIO_BIN"
fi
minio --version

# MinIO 数据目录
mkdir -p /opt/minio/data

echo "===== 6. 创建应用目录 ====="
mkdir -p /opt/vcs/{logs,frontend}

echo "===== 7. 验证 ====="
echo "  java    : $(java -version 2>&1 | head -1)"
echo "  mysql   : $(mysql --version)"
echo "  nginx   : $(nginx -v 2>&1)"
echo "  minio   : $(minio --version 2>&1 | head -1)"
echo ""
echo "============================================="
echo "  初始化完成！后续步骤："
echo "  1. 配置 MySQL root 密码（如需）"
echo "  2. 编辑 /opt/vcs/env.conf 填入实际凭据"
echo "  3. 启动 MinIO:  MINIO_ROOT_USER=xxx MINIO_ROOT_PASSWORD=xxx nohup minio server /opt/minio/data &"
echo "  4. 执行 deploy.sh 部署应用"
echo "============================================="
