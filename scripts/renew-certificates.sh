#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
COMPOSE=(docker compose -f docker-compose.prod.yaml)

if [ "${1:-}" = "--install-cron" ]; then
    shift
    # Заменяем также старую задачу со standalone-renew из setup-server.sh.
    if ! CURRENT_CRON=$(LC_ALL=C crontab -l 2>&1); then
        case "$CURRENT_CRON" in
            'no crontab for '*) CURRENT_CRON="" ;;
            *) printf '%s\n' "$CURRENT_CRON" >&2; exit 1 ;;
        esac
    fi
    CRON_LINE="0 3,15 * * * cd $(printf '%q' "$PWD") && bash scripts/renew-certificates.sh >> certificates.log 2>&1 # athletica-certificates:$PWD"
    {
        printf '%s\n' "$CURRENT_CRON" | awk -F ' # athletica-certificates:' -v project="$PWD" \
            '$NF != project && !(index($0, "cd " project " && ") && $0 ~ /certbot renew/)'
        printf '%s\n' "$CRON_LINE"
    } | crontab -
fi

# DOMAIN берём из запущенного nginx: .env читает Compose, а не shell.
DOMAIN=$("${COMPOSE[@]}" exec -T nginx printenv DOMAIN)
[ -n "$DOMAIN" ] || { echo "У nginx не задан DOMAIN" >&2; exit 1; }

# Все хосты из nginx/prod.conf.template; новый server_name — добавить и сюда.
CERT_ARGS=(-d "$DOMAIN" -d "www.$DOMAIN" -d "app.$DOMAIN" -d "clients.$DOMAIN" \
    -d "minio.$DOMAIN" -d "console.minio.$DOMAIN")
"${COMPOSE[@]}" run --rm -T certbot certonly \
    --webroot --webroot-path /var/www/certbot --cert-name "$DOMAIN" \
    --expand --keep-until-expiring --non-interactive "${CERT_ARGS[@]}" "$@"

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload
