#!/usr/bin/env bash
# 停止 VCS 后端
set -euo pipefail

APP_DIR="/opt/vcs"
PID_FILE="$APP_DIR/vcs.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID 文件不存在，VCS 可能未运行"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo "进程 $PID 已不存在，清理 PID 文件"
    rm -f "$PID_FILE"
    exit 0
fi

echo "正在停止 VCS (PID=$PID) ..."
kill "$PID"

# 等待最多 30 秒
for i in $(seq 1 30); do
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "VCS 已停止"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
done

echo "进程未在 30 秒内退出，强制终止"
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "VCS 已强制停止"
