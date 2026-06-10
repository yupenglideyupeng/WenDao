#!/bin/bash
# ============================================================
# WenDao — One-Click Deployment Script (预构建模式)
# ============================================================
# 本地打包 JAR + dist，上传到服务器后运行此脚本。
# Docker 只跑运行时，不在服务器上编译/构建。
#
# 前置条件：
#   1. 本地执行 mvn clean package 得到 wendao-admin.jar
#   2. 本地执行 npm run build:prod 得到 ui/dist/
#   3. 将 jar 和 dist 上传到 /opt/WenDao/
#
# Usage:
#   chmod +x deploy.sh
#   ./deploy.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================"
echo "  WenDao Docker Deployment"
echo "============================================"
echo ""

# ---- Step 0: Check prerequisites ----
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker is not installed."
    echo "  Install: curl -fsSL https://get.docker.com | bash"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "[ERROR] Docker Compose is not installed."
    echo "  Install: yum install -y docker-compose-plugin"
    exit 1
fi

echo "[OK] Docker $(docker --version)"
echo "[OK] Docker Compose $(docker compose version)"
echo ""

# ---- Step 1: Check .env.docker ----
if [ ! -f .env.docker ]; then
    echo "[INFO] .env.docker not found. Creating from .env.docker.example..."
    cp .env.docker.example .env.docker
    echo ""
    echo "============================================"
    echo "  ACTION REQUIRED"
    echo "============================================"
    echo "  Please edit .env.docker and fill in:"
    echo "    - DB_PASSWORD (strong MySQL password)"
    echo "    - TOKEN_SECRET (random 64-char string)"
    echo "    - NEWS_AI_API_KEY (DeepSeek API key, for AI features)"
    echo ""
    echo "  Then run ./deploy.sh again."
    echo "============================================"
    exit 0
fi

# Source environment variables
set -a
source .env.docker
set +a

echo "[OK] .env.docker loaded"
echo ""

# ---- Step 2: Check required artifacts ----
if [ ! -f wendao-admin.jar ]; then
    echo "[ERROR] wendao-admin.jar not found!"
    echo "  Please build locally and upload:"
    echo "    cd console && mvn clean package -Dmaven.test.skip=true"
    echo "    scp wendao-admin/target/wendao-admin.jar root@<server>:/opt/WenDao/"
    exit 1
fi
echo "[OK] wendao-admin.jar found"

if [ ! -d ui/dist ] || [ ! -f ui/dist/index.html ]; then
    echo "[ERROR] ui/dist/ not found!"
    echo "  Please build locally and upload:"
    echo "    cd ui && npm run build:prod"
    echo "    scp -r dist/ root@<server>:/opt/WenDao/ui/"
    exit 1
fi
echo "[OK] ui/dist/ found"
echo ""

# ---- Step 3: Create directories and copy SQL ----
echo "[INFO] Preparing..."
mkdir -p docker/mysql/init

if [ ! -f docker/mysql/init/02-wendao.sql ]; then
    echo "[INFO] Copying database init SQL..."
    cp console/sql/wendao.sql docker/mysql/init/02-wendao.sql
fi

echo "[OK] Ready"
echo ""

# ---- Step 4: Build images and start ----
echo "[INFO] Building Docker images..."
docker compose build backend frontend

echo ""
echo "[INFO] Starting all services..."
docker compose up -d

echo ""
echo "============================================"
echo "  Waiting for services to be healthy..."
echo "============================================"
echo ""

# ---- Step 5: Wait for healthy ----
MAX_WAIT=180
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    STATUS=$(docker compose ps --format json 2>/dev/null | grep -c '"Health":"healthy"' || true)
    TOTAL=$(docker compose ps --format json 2>/dev/null | grep -c '"Name"' || true)

    if [ "$STATUS" -eq "$TOTAL" ] && [ "$TOTAL" -eq 4 ]; then
        echo ""
        echo "[OK] All 4 services are healthy!"
        break
    fi

    echo "  [$WAITED s] Healthy: $STATUS / 4 services..."
    sleep 10
    WAITED=$((WAITED + 10))
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo ""
    echo "[WARN] Some services may not be healthy yet."
    echo "  Check status: docker compose ps"
fi

# ---- Step 6: Print summary ----
echo ""
echo "============================================"
echo "  Deployment Summary"
echo "============================================"
echo ""
docker compose ps
echo ""
echo "  Access URL:"
echo "    http://$(curl -s ifconfig.me 2>/dev/null || echo '<server-ip>'):3000"
echo ""
echo "  Default login: admin / admin123"
echo ""
echo "  Useful commands:"
echo "    docker compose ps              # Check service status"
echo "    docker compose logs -f backend # View backend logs"
echo "    docker compose restart backend # Restart backend"
echo "    docker compose restart frontend# Restart nginx"
echo ""
echo "============================================"
