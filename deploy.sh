#!/bin/bash
# ============================================================
# WenDao — One-Click Deployment Script
# ============================================================
# Run this on the CentOS server to deploy everything.
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

# ---- Step 2: Create directories ----
echo "[INFO] Creating directories..."
mkdir -p docker/mysql/init

# Copy the SQL init file if not already present
if [ ! -f docker/mysql/init/02-wendao.sql ]; then
    echo "[INFO] Copying database init SQL..."
    cp console/sql/wendao.sql docker/mysql/init/02-wendao.sql
fi

echo "[OK] Directories ready"
echo ""

# ---- Step 3: Build and start ----
echo "[INFO] Building Docker images..."
echo "  This may take 5-15 minutes on first run..."
echo ""

docker compose build --no-cache backend frontend

echo ""
echo "[INFO] Starting all services..."
docker compose up -d

echo ""
echo "============================================"
echo "  Deployment in progress..."
echo "============================================"
echo ""
echo "  Waiting for services to be healthy..."
echo "  (this may take 1-2 minutes)"
echo ""

# ---- Step 4: Wait for healthy ----
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

# ---- Step 5: Print summary ----
echo ""
echo "============================================"
echo "  Deployment Summary"
echo "============================================"
echo ""
docker compose ps
echo ""
echo "  Access URL:"
echo "    Frontend:  http://$(curl -s ifconfig.me 2>/dev/null || echo '<server-ip>'):3000"
echo ""
echo "  Default login: admin / admin123"
echo ""
echo "  Useful commands:"
echo "    docker compose ps              # Check service status"
echo "    docker compose logs -f backend # View backend logs"
echo "    docker compose logs -f frontend# View nginx logs"
echo "    docker compose restart backend # Restart backend"
echo ""
echo "  Note: Swagger and Druid are disabled by default in production."
echo "  To enable, set SWAGGER_ENABLED=true or DRUID_STAT_ENABLED=true in .env.docker"
echo ""
echo "============================================"
