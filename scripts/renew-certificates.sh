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

CERTIFICATES=$("${COMPOSE[@]}" run --rm -T certbot certificates --cert-name "$DOMAIN")
# shortcut: текстовый вывод Certbot, при смене формата обновить извлечение имён.
DOMAINS=$(printf '%s\n' "$CERTIFICATES" | sed -nE 's/^[[:space:]]*(Domains|Identifiers):[[:space:]]*//p')
if [ -z "$DOMAINS" ]; then
    printf 'Не удалось прочитать имена сертификата %s. Вывод Certbot:\n%s\n' "$DOMAIN" "$CERTIFICATES" >&2
    echo 'Проверьте список: docker compose -f docker-compose.prod.yaml run --rm -T certbot certificates' >&2
    exit 1
fi

# Сохраняем все прежние SAN, включая дополнительные домены продукта.
CERT_ARGS=()
read -r -a CERT_DOMAINS <<< "$DOMAINS"
for name in "${CERT_DOMAINS[@]}"; do CERT_ARGS+=(-d "$name"); done
CERT_ARGS+=(-d "app.$DOMAIN")
"${COMPOSE[@]}" run --rm -T certbot certonly \
    --webroot --webroot-path /var/www/certbot --cert-name "$DOMAIN" \
    --expand --keep-until-expiring --non-interactive "${CERT_ARGS[@]}" "$@"

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload
