#!/usr/bin/env bash
# 启动 VCS 后端（nohup）
set -euo pipefail

APP_DIR="/opt/vcs"
JAR="$APP_DIR/app.jar"
PID_FILE="$APP_DIR/vcs.pid"
LOG_DIR="$APP_DIR/logs"
ENV_FILE="$APP_DIR/env.conf"

mkdir -p "$LOG_DIR"

# 检查是否已在运行
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "VCS 已在运行 (PID=$OLD_PID)，请先执行 stop.sh"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# 加载环境变量
if [ -f "$ENV_FILE" ]; then
    # shellcheck source=/dev/null
    source "$ENV_FILE"
else
    echo "警告：未找到 $ENV_FILE，使用默认配置"
fi

echo "启动 VCS ..."
nohup java ${JVM_OPTS:-} -jar "$JAR" \
    --spring.profiles.active=prod \
    > "$LOG_DIR/app.log" 2>&1 &

echo $! > "$PID_FILE"
echo "VCS 已启动，PID=$(cat "$PID_FILE")，日志: $LOG_DIR/app.log"
