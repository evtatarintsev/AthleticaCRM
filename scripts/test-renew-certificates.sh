#!/usr/bin/env bash
# Проверка без Docker, crontab и запросов к Let's Encrypt.
set -euo pipefail

TEST_STATE=$(mktemp -d)
trap 'rm -rf "$TEST_STATE"' EXIT
export TEST_STATE
mkdir -p "$TEST_STATE/bin" "$TEST_STATE/project with spaces/scripts"
cp "$(dirname "${BASH_SOURCE[0]}")/renew-certificates.sh" "$TEST_STATE/project with spaces/scripts/"
export PATH="$TEST_STATE/bin:$PATH"

cat > "$TEST_STATE/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$TEST_STATE/docker.log"
case "$*" in
    *'nginx printenv DOMAIN') printf '%s\n' "${TEST_DOMAIN-example.com}" ;;
    *'certbot certificates --cert-name example.com')
        [ "${TEST_FAIL:-}" != list ] || exit 1
        printf '  Certificate Name: example.com\n    %s: %s\n' "${TEST_CERT_LABEL-Identifiers}" "${TEST_DOMAINS-example.com www.example.com minio.example.com console.minio.example.com old.example.org}"
        ;;
    *'certbot certonly '*) [ "${TEST_FAIL:-}" != issue ] ;;
    *'nginx nginx -t') [ "${TEST_FAIL:-}" != config ] ;;
    *'nginx nginx -s reload') ;;
    *) echo "Unexpected docker command: $*" >&2; exit 1 ;;
esac
MOCK
cat > "$TEST_STATE/bin/crontab" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
    -l)
        if [ "${TEST_FAIL:-}" = cron-read ]; then echo 'Cannot read crontab' >&2; exit 1; fi
        if [ ! -f "$TEST_STATE/crontab" ]; then echo 'no crontab for deploy' >&2; exit 1; fi
        cat "$TEST_STATE/crontab" ;;
    -) cat > "$TEST_STATE/crontab" ;;
    *) exit 1 ;;
esac
MOCK
chmod +x "$TEST_STATE/bin/docker" "$TEST_STATE/bin/crontab"
cd "$TEST_STATE/project with spaces"

printf '%s\n' \
    '0 1 * * * backup' \
    '0 3 * * * cd /other-project && docker compose run certbot renew --quiet' \
    "0 3,15 * * * other-command # athletica-certificates:$PWD-other" \
    "0 3 * * * cd $PWD && docker compose run certbot renew --quiet" > "$TEST_STATE/crontab"
bash scripts/renew-certificates.sh --install-cron
bash scripts/renew-certificates.sh --install-cron
[ "$(grep -c 'athletica-certificates:' "$TEST_STATE/crontab")" = 2 ]
[ "$(wc -l < "$TEST_STATE/crontab" | tr -d ' ')" = 4 ]
grep -qF '0 1 * * * backup' "$TEST_STATE/crontab"
grep -qF 'cd /other-project && docker compose run certbot renew' "$TEST_STATE/crontab"
grep -qF 'other-command # athletica-certificates:' "$TEST_STATE/crontab"
grep -qF '0 3,15 * * *' "$TEST_STATE/crontab"
grep -qF -- '--webroot --webroot-path /var/www/certbot --cert-name example.com --expand --keep-until-expiring --non-interactive -d example.com -d www.example.com -d minio.example.com -d console.minio.example.com -d old.example.org -d app.example.com -d clients.example.com' "$TEST_STATE/docker.log"
grep -qF 'nginx nginx -s reload' "$TEST_STATE/docker.log"
# Команда из cron работает и под /bin/sh, включая пробелы в пути проекта.
CRON_COMMAND=$(grep 'bash scripts/renew-certificates.sh' "$TEST_STATE/crontab" | cut -d ' ' -f6-)
sh -c "$CRON_COMMAND"

cp "$TEST_STATE/crontab" "$TEST_STATE/crontab.before"
if TEST_FAIL=cron-read bash scripts/renew-certificates.sh --install-cron; then
    echo 'Expected crontab read failure' >&2; exit 1
fi
cmp "$TEST_STATE/crontab.before" "$TEST_STATE/crontab"
rm "$TEST_STATE/crontab"
bash scripts/renew-certificates.sh --install-cron
[ "$(grep -c 'athletica-certificates:' "$TEST_STATE/crontab")" = 1 ]

# При ошибке чтения сертификата, выпуска или проверки nginx reload запрещён.
for failure in list issue config; do
    : > "$TEST_STATE/docker.log"
    if TEST_FAIL="$failure" bash scripts/renew-certificates.sh; then
        echo "Expected failure: $failure" >&2; exit 1
    fi
    ! grep -qF 'nginx nginx -s reload' "$TEST_STATE/docker.log"
done

for variable in TEST_DOMAIN TEST_DOMAINS; do
    : > "$TEST_STATE/docker.log"
    if env "$variable=" bash scripts/renew-certificates.sh 2> "$TEST_STATE/error.log"; then
        echo "Expected failure for empty $variable" >&2; exit 1
    fi
    ! grep -qF 'certbot certonly' "$TEST_STATE/docker.log"
done
grep -qF 'Вывод Certbot:' "$TEST_STATE/error.log"
grep -qF 'Certificate Name: example.com' "$TEST_STATE/error.log"
grep -qF 'run --rm -T certbot certificates' "$TEST_STATE/error.log"
! grep -qF 'setup-server.sh' "$TEST_STATE/error.log"

bash scripts/renew-certificates.sh --dry-run
grep -qF -- '--dry-run' "$TEST_STATE/docker.log"
TEST_CERT_LABEL=Domains bash scripts/renew-certificates.sh --dry-run
printf '%s\n' 'Certificate renewal checks passed'
