#!/bin/bash
# ============================================================
# WenDao — Update Backend Only
# ============================================================
# Rebuilds and redeploys the backend container without
# affecting the frontend, MySQL, or Redis.
#
# Usage:
#   chmod +x update-backend.sh
#   ./update-backend.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== WenDao Backend Update ==="
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

echo "[INFO] Building new backend image..."
docker compose build --no-cache backend

echo "[INFO] Restarting backend (rolling update)..."
docker compose up -d --no-deps backend

echo "[INFO] Waiting for backend to be healthy..."
sleep 5
for i in $(seq 1 30); do
    if docker compose ps backend | grep -q "healthy"; then
        echo "[OK] Backend is healthy!"
        break
    fi
    echo "  Waiting... ($i/30)"
    sleep 5
done

echo "[INFO] Cleaning up unused images..."
docker image prune -f

echo ""
echo "=== Backend update complete ==="
echo ""
docker compose logs --tail=20 backend
