#!/bin/bash
# ============================================================
# WenDao — Update Frontend Only
# ============================================================
# Rebuilds and redeploys the frontend container without
# affecting the backend, MySQL, or Redis.
#
# The nginx restart is near-instant, causing minimal downtime.
#
# Usage:
#   chmod +x update-frontend.sh
#   ./update-frontend.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== WenDao Frontend Update ==="
echo ""

# Source env
if [ -f .env.docker ]; then
    set -a
    source .env.docker
    set +a
fi

# Pull latest code if in a git repo
if [ -d .git ]; then
    echo "[INFO] Pulling latest code..."
    git pull --ff-only || echo "[WARN] git pull failed, continuing with current code..."
fi

echo "[INFO] Building new frontend image..."
docker compose build --no-cache frontend

echo "[INFO] Restarting frontend (rolling update, ~1s downtime)..."
docker compose up -d --no-deps frontend

echo "[INFO] Cleaning up unused images..."
docker image prune -f

echo ""
echo "=== Frontend update complete ==="
echo ""
docker compose logs --tail=10 frontend
