#!/usr/bin/env sh
# Раскладка /etc/letsencrypt, которую ждёт боевой nginx/prod.conf.template, для домена стенда e2e.test:
# самоподписанный сертификат, настройки TLS и группа DH ffdhe2048 (RFC 7919).
set -eu

STAND="$(cd "$(dirname "$0")" && pwd)"
OUT="$STAND/.letsencrypt"
LIVE="$OUT/live/e2e.test"

mkdir -p "$LIVE"

# Сертификат без нового имени (выпущен до появления хоста) перевыпускается.
if ! grep -qs clients.e2e.test "$LIVE/names"; then
  openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout "$LIVE/privkey.pem" -out "$LIVE/fullchain.pem" \
    -subj "/CN=e2e.test" \
    -addext "subjectAltName=DNS:e2e.test,DNS:app.e2e.test,DNS:clients.e2e.test,DNS:minio.e2e.test,DNS:console.minio.e2e.test" \
    -addext "extendedKeyUsage=serverAuth" 2>/dev/null
  echo "e2e.test app.e2e.test clients.e2e.test minio.e2e.test console.minio.e2e.test" > "$LIVE/names"
fi

cat > "$OUT/options-ssl-nginx.conf" <<'CONF'
ssl_session_cache shared:le_nginx_SSL:10m;
ssl_session_timeout 1440m;
ssl_session_tickets off;
ssl_protocols TLSv1.2 TLSv1.3;
ssl_prefer_server_ciphers off;
CONF

cp "$STAND/ssl-dhparams.pem" "$OUT/ssl-dhparams.pem"
