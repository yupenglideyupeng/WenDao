#!/bin/bash
# ============================================================
# WenDao — SSL Certificate Setup (Let's Encrypt via certbot)
# ============================================================
# Obtains a free Let's Encrypt SSL certificate and configures
# auto-renewal via cron.
#
# Prerequisites:
#   - Domain DNS must already point to this server
#   - The HTTP-only frontend must be running (port 80)
#
# Usage:
#   chmod +x setup-ssl.sh
#   ./setup-ssl.sh wendao.yourdomain.com [admin@yourdomain.com]
# ============================================================
set -e

DOMAIN=${1:?"Usage: $0 <domain> [email]"}
EMAIL=${2:-admin@${DOMAIN}}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

SSL_DIR="$SCRIPT_DIR/docker/nginx/ssl"
CERTBOT_DIR="$SCRIPT_DIR/docker/nginx/certbot"

echo "============================================"
echo "  WenDao SSL Certificate Setup"
echo "============================================"
echo ""
echo "  Domain: ${DOMAIN}"
echo "  Email:  ${EMAIL}"
echo ""

# ---- Step 1: Ensure directories exist ----
mkdir -p "$SSL_DIR" "$CERTBOT_DIR"

# ---- Step 2: Check DNS ----
echo "[INFO] Checking DNS resolution for ${DOMAIN}..."
if ! host "${DOMAIN}" > /dev/null 2>&1; then
    echo "[ERROR] ${DOMAIN} does not resolve to an IP address."
    echo "  Please configure an A record in Alibaba Cloud DNS first."
    exit 1
fi
echo "[OK] ${DOMAIN} resolves to $(host ${DOMAIN} | awk '{print $NF}')"

# ---- Step 3: Install certbot ----
echo ""
echo "[INFO] Installing certbot..."
if ! command -v certbot &> /dev/null; then
    if command -v yum &> /dev/null; then
        # CentOS / RHEL
        yum install -y epel-release 2>/dev/null || true
        yum install -y certbot
    elif command -v apt-get &> /dev/null; then
        # Ubuntu / Debian
        apt-get update
        apt-get install -y certbot
    else
        echo "[ERROR] Cannot install certbot. Please install manually."
        exit 1
    fi
fi
echo "[OK] certbot $(certbot --version)"

# ---- Step 4: Stop frontend temporarily to free port 80 ----
echo ""
echo "[INFO] Temporarily stopping frontend for certificate challenge..."
docker compose stop frontend 2>/dev/null || true

# ---- Step 5: Obtain certificate (standalone mode) ----
echo "[INFO] Requesting SSL certificate from Let's Encrypt..."
certbot certonly --standalone \
    -d "${DOMAIN}" \
    --email "${EMAIL}" \
    --agree-tos \
    --non-interactive \
    --preferred-challenges http

# ---- Step 6: Copy certs to nginx SSL directory ----
echo "[INFO] Copying certificates to nginx SSL directory..."
cp /etc/letsencrypt/live/${DOMAIN}/fullchain.pem "$SSL_DIR/fullchain.pem"
cp /etc/letsencrypt/live/${DOMAIN}/privkey.pem "$SSL_DIR/privkey.pem"
chmod 600 "$SSL_DIR/privkey.pem"

echo "[OK] Certificates copied to $SSL_DIR/"

# ---- Step 7: Switch docker-compose to SSL config ----
echo ""
echo "============================================"
echo "  ACTION REQUIRED"
echo "============================================"
echo ""
echo "  Edit docker-compose.yml and change the frontend volumes section:"
echo ""
echo "  FROM:"
echo "    - ./docker/nginx/default.conf.template:/etc/nginx/templates/default.conf.template:ro"
echo ""
echo "  TO:"
echo "    - ./docker/nginx/default-ssl.conf.template:/etc/nginx/templates/default.conf.template:ro"
echo ""
echo "  Then restart the frontend:"
echo "    docker compose up -d --no-deps frontend"
echo ""
echo "============================================"

# ---- Step 8: Restart frontend (HTTP mode for now) ----
echo ""
echo "[INFO] Restarting frontend (HTTP mode for now)..."
docker compose up -d frontend

# ---- Step 9: Set up auto-renewal cron ----
CRON_CMD="0 3 * * * certbot renew --quiet --standalone --pre-hook 'docker compose -f $SCRIPT_DIR/docker-compose.yml stop frontend' --post-hook 'cp /etc/letsencrypt/live/${DOMAIN}/fullchain.pem $SSL_DIR/fullchain.pem && cp /etc/letsencrypt/live/${DOMAIN}/privkey.pem $SSL_DIR/privkey.pem && docker compose -f $SCRIPT_DIR/docker-compose.yml start frontend'"

# Add to crontab if not already present
if ! crontab -l 2>/dev/null | grep -q "certbot renew.*${DOMAIN}"; then
    (crontab -l 2>/dev/null || true; echo "$CRON_CMD") | crontab -
    echo "[OK] Auto-renewal cron job added (runs daily at 3:00 AM)"
else
    echo "[INFO] Auto-renewal cron job already exists"
fi

echo ""
echo "============================================"
echo "  SSL Setup Complete!"
echo "============================================"
echo ""
echo "  Certificates: $SSL_DIR/"
echo "  Auto-renewal: Daily at 3:00 AM"
echo "  Expiry check: certbot certificates"
echo ""
echo "  After switching to SSL config:"
echo "    https://${DOMAIN}"
echo ""
echo "============================================"
