#!/usr/bin/env bash
#
# Первичная настройка сервера AthleticaCRM.
#
# Запускается НА ЛОКАЛЬНОЙ МАШИНЕ из корня репозитория:
#
#     ./scripts/setup-server.sh
#
# Единственное, что нужно сделать руками до запуска, — положить публичный
# SSH-ключ этой машины в authorized_keys root'а на сервере. Скрипт начинается
# с проверки этого доступа и подсказывает команду, если ключа ещё нет.
#
# Скрипт идемпотентен: повторный запуск не ломает уже настроенный сервер.

set -euo pipefail

# ---------------------------------------------------------------- оформление

if [ -t 1 ]; then
    B=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GRN=$'\033[32m'
    YEL=$'\033[33m'; CYA=$'\033[36m'; N=$'\033[0m'
else
    B=''; DIM=''; RED=''; GRN=''; YEL=''; CYA=''; N=''
fi

STEP_NO=0

step() { STEP_NO=$((STEP_NO + 1)); printf '\n%s━━━ Шаг %d. %s%s\n\n' "$B$CYA" "$STEP_NO" "$1" "$N"; }
info() { printf '%s\n' "$1"; }
hint() { printf '%s%s%s\n' "$DIM" "$1" "$N"; }
ok()   { printf '%s✓%s %s\n' "$GRN" "$N" "$1"; }
warn() { printf '%s!%s %s\n' "$YEL" "$N" "$1"; }
die()  { printf '%s✗ %s%s\n' "$RED" "$1" "$N" >&2; exit 1; }

# ------------------------------------------------------------------- ввод

# Спрашивает значение с необязательным значением по умолчанию.
ask() {
    local prompt="$1" default="${2:-}" answer
    if [ -n "$default" ]; then
        printf '%s %s[%s]%s: ' "$prompt" "$DIM" "$default" "$N" >&2
    else
        printf '%s: ' "$prompt" >&2
    fi
    read -r answer </dev/tty
    [ -z "$answer" ] && answer="$default"
    printf '%s' "$answer"
}

# Спрашивает значение, пока не введено непустое.
ask_required() {
    local value
    while true; do
        value=$(ask "$1" "${2:-}")
        [ -n "$value" ] && { printf '%s' "$value"; return; }
        printf '%sЗначение обязательно.%s\n' "$YEL" "$N" >&2
    done
}

# Спрашивает секрет, не отображая ввод.
ask_secret() {
    local answer
    printf '%s: ' "$1" >&2
    read -rs answer </dev/tty
    printf '\n' >&2
    printf '%s' "$answer"
}

# Вопрос да/нет; второй аргумент — ответ по умолчанию (y или n).
confirm() {
    local prompt="$1" default="${2:-y}" answer suffix
    if [ "$default" = "y" ]; then suffix="[Y/n]"; else suffix="[y/N]"; fi
    printf '%s %s%s%s: ' "$prompt" "$DIM" "$suffix" "$N" >&2
    read -r answer </dev/tty
    [ -z "$answer" ] && answer="$default"
    case "$answer" in [yYдД]*) return 0 ;; *) return 1 ;; esac
}

# Экранирует аргументы для передачи в удалённый bash.
qq() { printf '%q ' "$@"; }

# --------------------------------------------------------------- преамбула

printf '\n%s╔══════════════════════════════════════════════════════╗%s\n' "$B$CYA" "$N"
printf '%s║   AthleticaCRM — первичная настройка сервера          ║%s\n' "$B$CYA" "$N"
printf '%s╚══════════════════════════════════════════════════════╝%s\n' "$B$CYA" "$N"

[ -f docker-compose.prod.yaml ] || die "Запускать из корня репозитория (не вижу docker-compose.prod.yaml)."

for tool in ssh scp ssh-keygen openssl base64 git; do
    command -v "$tool" >/dev/null 2>&1 || die "Не найдена утилита '$tool' — установите её и запустите скрипт снова."
done

# ============================================================= 1. SSH-доступ

step "Доступ по SSH к серверу"

info "Это единственное, что нужно подготовить руками: публичный ключ этой машины"
info "должен лежать в ~/.ssh/authorized_keys пользователя root на сервере."
printf '\n'

LOCAL_KEYS=$(ls ~/.ssh/*.pub 2>/dev/null || true)
if [ -n "$LOCAL_KEYS" ]; then
    hint "Публичные ключи на этой машине:"
    for k in $LOCAL_KEYS; do hint "    $k"; done
    printf '\n'
fi

SERVER_HOST=$(ask_required "IP или hostname сервера")
ROOT_USER=$(ask "Пользователь с правами root на сервере" "root")

SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"

while true; do
    printf 'Проверяю доступ %s@%s… ' "$ROOT_USER" "$SERVER_HOST"
    # shellcheck disable=SC2086
    if ssh $SSH_OPTS -o BatchMode=yes "$ROOT_USER@$SERVER_HOST" true 2>/dev/null; then
        printf '%sесть%s\n' "$GRN" "$N"
        break
    fi
    printf '%sнет%s\n\n' "$RED" "$N"
    info "Ключ ещё не прописан на сервере. Скопируйте его с этой машины:"
    printf '\n    %sssh-copy-id -i ~/.ssh/id_ed25519.pub %s@%s%s\n\n' "$B" "$ROOT_USER" "$SERVER_HOST" "$N"
    hint "Либо вручную: зайдите на сервер по паролю и добавьте содержимое"
    hint "своего .pub-файла в /root/.ssh/authorized_keys."
    printf '\n'
    confirm "Повторить проверку?" y || die "Прервано пользователем."
done

# shellcheck disable=SC2086
[ "$(ssh $SSH_OPTS "$ROOT_USER@$SERVER_HOST" 'id -u')" = "0" ] \
    || die "Пользователь $ROOT_USER не root. Скрипту нужны root-права для установки Docker."

ssh_root() { ssh $SSH_OPTS "$ROOT_USER@$SERVER_HOST" "$@"; }

ok "Доступ к серверу есть"

# ============================================================ 2. Параметры

step "Параметры установки"

DOMAIN=$(ask_required "Домен (без https:// и без слеша), например athletictools.ru")
ACME_EMAIL=$(ask_required "E-mail для Let's Encrypt (уведомления об истечении сертификата)" "admin@$DOMAIN")

GIT_REMOTE=$(git config --get remote.origin.url 2>/dev/null || true)
REPO_GUESS=$(printf '%s' "$GIT_REMOTE" | sed -E 's#^.*github\.com[:/]##; s#\.git$##')
printf '\n'
hint "Репозиторий на GitHub в формате owner/repo — оттуда сервер тянет docker-образы."
GITHUB_REPO=$(ask_required "Репозиторий GitHub" "$REPO_GUESS")
GITHUB_REPO_LC=$(printf '%s' "$GITHUB_REPO" | tr '[:upper:]' '[:lower:]')
GITHUB_OWNER=${GITHUB_REPO%%/*}

printf '\n'
DEPLOY_USER=$(ask "Пользователь на сервере для деплоя" "deploy")

while true; do
    DEPLOY_PATH=$(ask "Каталог проекта на сервере" "/opt/athletica-crm")
    PROJECT_NAME=$(basename "$DEPLOY_PATH")
    case "$PROJECT_NAME" in
        [a-z0-9]*[!a-zA-Z0-9_-]*) warn "В имени каталога допустимы только строчные буквы, цифры, дефис и подчёркивание." ;;
        [a-z0-9]*) break ;;
        *) warn "Имя каталога должно начинаться со строчной буквы или цифры." ;;
    esac
done

# Docker Compose называет тома по имени каталога проекта.
VOLUME_LETSENCRYPT="${PROJECT_NAME}_letsencrypt"

CERT_DOMAINS="$DOMAIN www.$DOMAIN minio.$DOMAIN console.minio.$DOMAIN"

printf '\n'
ok "Том с сертификатами будет называться: $VOLUME_LETSENCRYPT"

# ================================================================= 3. DNS

step "Проверка DNS"

info "Все эти имена должны A-записью указывать на сервер:"
printf '\n'
for d in $CERT_DOMAINS; do printf '    %s\n' "$d"; done
printf '\n'

# Публичный адрес сервера: если подключались по IP — он и есть, иначе резолвим имя.
case "$SERVER_HOST" in
    *[!0-9.]*) SERVER_IP=$(dig +short "$SERVER_HOST" A 2>/dev/null | tail -1) ;;
    *)         SERVER_IP="$SERVER_HOST" ;;
esac
[ -z "$SERVER_IP" ] && SERVER_IP="$SERVER_HOST"

if command -v dig >/dev/null 2>&1; then
    DNS_BAD=0
    for d in $CERT_DOMAINS; do
        RESOLVED=$(dig +short "$d" A | tail -1)
        if [ -z "$RESOLVED" ]; then
            warn "$d — нет A-записи"
            DNS_BAD=1
        elif [ "$RESOLVED" != "$SERVER_IP" ] && [ "$RESOLVED" != "$SERVER_HOST" ]; then
            warn "$d → $RESOLVED (ожидался $SERVER_IP)"
            DNS_BAD=1
        else
            ok "$d → $RESOLVED"
        fi
    done
    if [ "$DNS_BAD" = "1" ]; then
        printf '\n'
        warn "Let's Encrypt не выдаст сертификат, пока DNS не отвечает правильно."
        hint "Записи создаются в панели вашего DNS-провайдера; распространение занимает 5–30 минут."
        confirm "Всё равно продолжить?" n || die "Прервано: сначала настройте DNS."
    fi
else
    warn "Утилита dig не найдена — проверку DNS пропускаю."
fi

# ============================================================== 4. Секреты

step "Переменные окружения"

ENV_BACKUP_DIR="$HOME/.athletica-crm"
ENV_BACKUP="$ENV_BACKUP_DIR/$DOMAIN.env"

REMOTE_ENV_EXISTS=no
if ssh_root "test -f $(qq "$DEPLOY_PATH/.env")" 2>/dev/null; then
    REMOTE_ENV_EXISTS=yes
fi

REUSE_ENV=no
if [ "$REMOTE_ENV_EXISTS" = "yes" ]; then
    warn "На сервере уже есть $DEPLOY_PATH/.env"
    hint "Перегенерация создаст новые пароли к БД — для уже работающей базы это сломает доступ."
    if confirm "Оставить существующий .env как есть?" y; then
        REUSE_ENV=yes
        ok "Использую .env, который уже лежит на сервере"
    fi
fi

if [ "$REUSE_ENV" = "no" ]; then
    info "Пароли к PostgreSQL, MinIO и JWT-секрет генерируются автоматически."
    printf '\n'

    POSTGRES_USER=athletica
    POSTGRES_PASSWORD=$(openssl rand -hex 24)
    JWT_SECRET=$(openssl rand -hex 32)
    MINIO_ACCESS_KEY=athletica
    MINIO_SECRET_KEY=$(openssl rand -hex 20)

    hint "SMTP — почта, с которой уходят письма пользователям (приглашения, сброс пароля)."
    hint "Данные берутся в панели вашего почтового провайдера (Unisender, Mailgun, Яндекс 360 и т.п.)."
    SMTP_HOST=$(ask_required "SMTP-хост" "smtp.go1.unisender.ru")
    SMTP_USERNAME=$(ask_required "SMTP-логин" "noreply@$DOMAIN")
    SMTP_PASSWORD=$(ask_secret "SMTP-пароль")
    SMTP_FROM_ADDRESS=$(ask_required "Адрес отправителя" "noreply@$DOMAIN")

    printf '\n'
    hint "ЮKassa — приём платежей. shopId и секретный ключ: панель ЮKassa → Настройки → Магазин."
    hint "Если приём платежей пока не нужен, оставьте пустыми и включите тестовый режим."
    YOOKASSA_SHOP_ID=$(ask "shopId ЮKassa" "")
    YOOKASSA_SECRET_KEY=$(ask_secret "Секретный ключ ЮKassa (можно пустым)")
    if confirm "Тестовый режим ЮKassa?" y; then YOOKASSA_TEST_MODE=true; else YOOKASSA_TEST_MODE=false; fi
    YOOKASSA_RETURN_URL="https://$DOMAIN/payment/complete"

    printf '\n'
    hint "Sentry/GlitchTip DSN — сбор ошибок сервера. Необязательно, можно оставить пустым."
    SENTRY_DSN=$(ask "Sentry DSN" "")

    mkdir -p "$ENV_BACKUP_DIR"
    chmod 700 "$ENV_BACKUP_DIR"
    umask 077
    cat > "$ENV_BACKUP" <<ENV_EOF
# Сгенерировано scripts/setup-server.sh для $DOMAIN
# $(date '+%Y-%m-%d %H:%M:%S')

GITHUB_REPOSITORY=$GITHUB_REPO_LC
DOMAIN=$DOMAIN

POSTGRES_USER=$POSTGRES_USER
POSTGRES_PASSWORD=$POSTGRES_PASSWORD

JWT_SECRET=$JWT_SECRET

MINIO_ACCESS_KEY=$MINIO_ACCESS_KEY
MINIO_SECRET_KEY=$MINIO_SECRET_KEY
MINIO_BUCKET=athletica-crm
MINIO_PUBLIC_ENDPOINT=https://minio.$DOMAIN

SMTP_HOST=$SMTP_HOST
SMTP_USERNAME=$SMTP_USERNAME
SMTP_PASSWORD=$SMTP_PASSWORD
SMTP_FROM_ADDRESS=$SMTP_FROM_ADDRESS
SENTRY_DSN=$SENTRY_DSN

YOOKASSA_SHOP_ID=$YOOKASSA_SHOP_ID
YOOKASSA_SECRET_KEY=$YOOKASSA_SECRET_KEY
YOOKASSA_TEST_MODE=$YOOKASSA_TEST_MODE
YOOKASSA_RETURN_URL=$YOOKASSA_RETURN_URL
ENV_EOF
    umask 022
    ok "Копия .env сохранена локально: $ENV_BACKUP"
    hint "Там лежат пароли к БД — не теряйте файл и не коммитьте его."
fi

printf '\n'
hint "Токен GitHub для скачивания приватных docker-образов с ghcr.io."
hint "Создать: https://github.com/settings/tokens/new?scopes=read:packages&description=athletica-server-pull"
hint "Тип — classic, срок — No expiration, единственный скоуп — read:packages."
GHCR_TOKEN=$(ask_secret "GitHub PAT (read:packages)")
[ -n "$GHCR_TOKEN" ] || die "Без токена сервер не сможет скачать образы."

# ========================================================= 5. Ключ деплоя

step "SSH-ключ для автодеплоя"

DEPLOY_KEY="$HOME/.ssh/athletica_deploy"

if [ -f "$DEPLOY_KEY" ]; then
    ok "Ключ уже есть: $DEPLOY_KEY"
else
    ssh-keygen -t ed25519 -N "" -C "athletica-deploy" -f "$DEPLOY_KEY" >/dev/null
    ok "Создан ключ: $DEPLOY_KEY"
fi

DEPLOY_PUBKEY=$(cat "$DEPLOY_KEY.pub")
hint "Приватная часть этого ключа уйдёт в секрет DEPLOY_SSH_KEY на GitHub —"
hint "им GitHub Actions будет заходить на сервер под пользователем $DEPLOY_USER."

case "$PROJECT_NAME" in
    *athletica*) SSH_ALIAS="$PROJECT_NAME" ;;
    *)           SSH_ALIAS="athletica-$PROJECT_NAME" ;;
esac
if ! grep -qE "^Host[[:space:]]+$SSH_ALIAS\$" "$HOME/.ssh/config" 2>/dev/null; then
    if confirm "Добавить алиас '$SSH_ALIAS' в ~/.ssh/config для ручных заходов?" y; then
        mkdir -p "$HOME/.ssh"
        chmod 700 "$HOME/.ssh"
        cat >> "$HOME/.ssh/config" <<CONFIG_EOF

Host $SSH_ALIAS
    HostName $SERVER_HOST
    User $DEPLOY_USER
    IdentityFile $DEPLOY_KEY
    IdentitiesOnly yes
CONFIG_EOF
        ok "Теперь можно заходить: ssh $SSH_ALIAS"
    fi
fi

# ==================================================== 6. Подготовка сервера

step "Подготовка сервера: Docker, пользователь, каталоги"

info "Ставлю Docker (если ещё не стоит), создаю пользователя $DEPLOY_USER и каталоги…"

# Удалённые скрипты передаём файлом, а не через stdin: docker compose run
# забирает stdin себе и сожрал бы остаток скрипта, оборвав его без ошибки.
REMOTE_SCRIPT=$(mktemp)
trap 'rm -f "$REMOTE_SCRIPT"' EXIT

cat > "$REMOTE_SCRIPT" <<'REMOTE_PREPARE'
set -euo pipefail
DEPLOY_USER="$1"; DEPLOY_PATH="$2"; DEPLOY_PUBKEY="$3"

if ! command -v docker >/dev/null 2>&1; then
    echo "  · устанавливаю Docker Engine"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl gnupg >/dev/null
    install -m 0755 -d /etc/apt/keyrings
    DISTRO=$(. /etc/os-release && echo "$ID")
    CODENAME=$(. /etc/os-release && echo "${VERSION_CODENAME:-$(lsb_release -cs 2>/dev/null)}")
    rm -f /etc/apt/keyrings/docker.gpg
    curl -fsSL "https://download.docker.com/linux/$DISTRO/gpg" | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$DISTRO $CODENAME stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
    systemctl enable --now docker
else
    echo "  · Docker уже установлен: $(docker --version)"
fi

if ! id -u "$DEPLOY_USER" >/dev/null 2>&1; then
    useradd -m -s /bin/bash "$DEPLOY_USER"
    echo "  · создан пользователь $DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"

HOME_DIR=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$HOME_DIR/.ssh"
touch "$HOME_DIR/.ssh/authorized_keys"
if ! grep -qxF "$DEPLOY_PUBKEY" "$HOME_DIR/.ssh/authorized_keys"; then
    printf '%s\n' "$DEPLOY_PUBKEY" >> "$HOME_DIR/.ssh/authorized_keys"
    echo "  · ключ деплоя добавлен в authorized_keys"
fi
chmod 600 "$HOME_DIR/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "$HOME_DIR/.ssh/authorized_keys"

install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DEPLOY_PATH" "$DEPLOY_PATH/nginx" "$DEPLOY_PATH/ssl"

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
    ufw allow 22/tcp >/dev/null 2>&1 || true
    ufw allow 80/tcp >/dev/null 2>&1 || true
    ufw allow 443/tcp >/dev/null 2>&1 || true
    echo "  · открыты порты 22, 80, 443 в ufw"
fi
REMOTE_PREPARE

# shellcheck disable=SC2086
scp $SSH_OPTS -q "$REMOTE_SCRIPT" "$ROOT_USER@$SERVER_HOST:/tmp/athletica-prepare.sh"
ssh_root "bash /tmp/athletica-prepare.sh $(qq "$DEPLOY_USER" "$DEPLOY_PATH" "$DEPLOY_PUBKEY"); rc=\$?; rm -f /tmp/athletica-prepare.sh; exit \$rc"

ok "Сервер подготовлен"

ssh_deploy() { ssh $SSH_OPTS -i "$DEPLOY_KEY" -o IdentitiesOnly=yes "$DEPLOY_USER@$SERVER_HOST" "$@"; }

printf 'Проверяю вход под %s ключом деплоя… ' "$DEPLOY_USER"
if ssh_deploy true 2>/dev/null; then
    printf '%sок%s\n' "$GRN" "$N"
else
    die "Не удаётся зайти как $DEPLOY_USER с ключом $DEPLOY_KEY."
fi

# ================================================== 7. Копирование файлов

step "Копирование конфигурации на сервер"

SCP_OPTS="-o StrictHostKeyChecking=accept-new -i $DEPLOY_KEY -o IdentitiesOnly=yes"

# shellcheck disable=SC2086
scp $SCP_OPTS -q docker-compose.prod.yaml "$DEPLOY_USER@$SERVER_HOST:$DEPLOY_PATH/docker-compose.prod.yaml"
# shellcheck disable=SC2086
scp $SCP_OPTS -q nginx/prod.conf.template "$DEPLOY_USER@$SERVER_HOST:$DEPLOY_PATH/nginx/prod.conf.template"
ok "docker-compose.prod.yaml, nginx/prod.conf.template"

if [ "$REUSE_ENV" = "no" ]; then
    # shellcheck disable=SC2086
    scp $SCP_OPTS -q "$ENV_BACKUP" "$DEPLOY_USER@$SERVER_HOST:$DEPLOY_PATH/.env"
    ssh_deploy "chmod 600 $(qq "$DEPLOY_PATH/.env")"
    ok ".env"
fi

# Конфиг TLS, который nginx подключает из тома letsencrypt.
# Держим его в скрипте, а не тянем из интернета: путь в репозитории certbot
# уже менялся, и curl тихо клал в файл страницу 404.
ssh_deploy "cat > $(qq "$DEPLOY_PATH/ssl/options-ssl-nginx.conf")" <<'SSL_OPTIONS'
ssl_session_cache shared:le_nginx_SSL:10m;
ssl_session_timeout 1440m;
ssl_session_tickets off;

ssl_protocols TLSv1.2 TLSv1.3;
ssl_prefer_server_ciphers off;

ssl_ciphers "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384";
SSL_OPTIONS
ok "ssl/options-ssl-nginx.conf"

# ==================================================== 8. Образы, TLS, запуск

step "Docker-образы, TLS-сертификат, запуск"

info "Логин в ghcr.io, выпуск сертификата и старт стека. Первый запуск займёт несколько минут."
printf '\n'

# Токен передаём файлом, а не аргументом командной строки: аргументы видны
# в выводе ps на сервере, пока идёт деплой.
TOKEN_TMP=$(mktemp)
trap 'rm -f "$TOKEN_TMP" "$REMOTE_SCRIPT"' EXIT
printf '%s' "$GHCR_TOKEN" > "$TOKEN_TMP"
chmod 600 "$TOKEN_TMP"
# shellcheck disable=SC2086
scp $SCP_OPTS -q "$TOKEN_TMP" "$DEPLOY_USER@$SERVER_HOST:$DEPLOY_PATH/.ghcr_token"
ssh_deploy "chmod 600 $(qq "$DEPLOY_PATH/.ghcr_token")"

cat > "$REMOTE_SCRIPT" <<'REMOTE_DEPLOY'
set -euo pipefail
DEPLOY_PATH="$1"; GH_OWNER="$2"; DOMAIN="$3"
ACME_EMAIL="$4"; VOL_LE="$5"; CERT_DOMAINS="$6"

cd "$DEPLOY_PATH"
COMPOSE="docker compose -f docker-compose.prod.yaml"

echo "  · логин в ghcr.io"
docker login ghcr.io -u "$GH_OWNER" --password-stdin < .ghcr_token >/dev/null
rm -f .ghcr_token

echo "  · освобождаю порт 80 для certbot"
$COMPOSE down --remove-orphans >/dev/null 2>&1 || true

# Проверку делаем через compose, а не docker run: так том letsencrypt создаётся
# самим compose и получает его метки. Том, созданный мимо compose, ломает up.
if $COMPOSE run --rm --entrypoint sh certbot -c "test -d /etc/letsencrypt/live/$DOMAIN" </dev/null >/dev/null 2>&1; then
    echo "  · сертификат для $DOMAIN уже есть, пропускаю выпуск"
else
    echo "  · запрашиваю сертификат Let's Encrypt"
    CERT_ARGS=""
    for d in $CERT_DOMAINS; do CERT_ARGS="$CERT_ARGS -d $d"; done
    # shellcheck disable=SC2086
    $COMPOSE run --rm -p 80:80 certbot certonly --standalone \
        $CERT_ARGS \
        --email "$ACME_EMAIL" --agree-tos --no-eff-email --non-interactive </dev/null
fi

echo "  · дополняю том letsencrypt файлами для nginx"
docker run --rm \
    -v "$VOL_LE:/etc/letsencrypt" \
    -v "$DEPLOY_PATH/ssl:/src:ro" \
    alpine:latest sh -c '
        set -e
        cp /src/options-ssl-nginx.conf /etc/letsencrypt/options-ssl-nginx.conf
        if [ ! -s /etc/letsencrypt/ssl-dhparams.pem ]; then
            apk add --no-cache openssl >/dev/null
            openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048 2>/dev/null
        fi
    '

echo "  · скачиваю образы"
$COMPOSE pull --quiet

echo "  · запускаю стек"
$COMPOSE up -d

echo "  · настраиваю автопродление сертификата"
CRON_LINE="0 3 * * * cd $DEPLOY_PATH && docker compose -f docker-compose.prod.yaml run --rm certbot renew --quiet && docker compose -f docker-compose.prod.yaml exec -T nginx nginx -s reload"
if ! crontab -l 2>/dev/null | grep -qF "certbot renew"; then
    (crontab -l 2>/dev/null || true; echo "$CRON_LINE") | crontab -
    echo "    добавлена задача в crontab (каждую ночь в 03:00)"
fi

echo "  · проверяю состояние контейнеров"
sleep 8
$COMPOSE ps
NGINX_STATE=$($COMPOSE ps --format '{{.Service}} {{.State}}' | awk '$1 == "nginx" { print $2 }')
if [ "$NGINX_STATE" != "running" ]; then
    echo
    echo "nginx не запустился (состояние: ${NGINX_STATE:-нет контейнера}). Последние строки лога:"
    $COMPOSE logs --tail 20 nginx
    exit 1
fi
REMOTE_DEPLOY

# shellcheck disable=SC2086
scp $SCP_OPTS -q "$REMOTE_SCRIPT" "$DEPLOY_USER@$SERVER_HOST:/tmp/athletica-deploy.sh"
ssh_deploy "bash /tmp/athletica-deploy.sh $(qq "$DEPLOY_PATH" "$GITHUB_OWNER" "$DOMAIN" "$ACME_EMAIL" "$VOLUME_LETSENCRYPT" "$CERT_DOMAINS"); rc=\$?; rm -f /tmp/athletica-deploy.sh; exit \$rc"

ok "Стек запущен"

# =================================================== 9. Секреты GitHub

step "Секреты GitHub Actions для автодеплоя"

GH_READY=no
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    GH_READY=yes
fi

if [ "$GH_READY" = "yes" ] && confirm "Прописать секреты в $GITHUB_REPO через gh CLI?" y; then
    gh secret set DEPLOY_HOST     -R "$GITHUB_REPO" -b "$SERVER_HOST"
    gh secret set DEPLOY_USER     -R "$GITHUB_REPO" -b "$DEPLOY_USER"
    gh secret set DEPLOY_PATH     -R "$GITHUB_REPO" -b "$DEPLOY_PATH"
    gh secret set GHCR_READ_TOKEN -R "$GITHUB_REPO" -b "$GHCR_TOKEN"
    gh secret set DEPLOY_SSH_KEY  -R "$GITHUB_REPO" < "$DEPLOY_KEY"
    ok "Секреты записаны"
else
    [ "$GH_READY" = "no" ] && hint "gh CLI не установлен или не авторизован (gh auth login) — пропишите секреты вручную."
    printf '\n'
    info "Откройте https://github.com/$GITHUB_REPO/settings/secrets/actions"
    info "и добавьте пять секретов (New repository secret):"
    printf '\n'
    printf '    %-18s %s\n' "DEPLOY_HOST" "$SERVER_HOST"
    printf '    %-18s %s\n' "DEPLOY_USER" "$DEPLOY_USER"
    printf '    %-18s %s\n' "DEPLOY_PATH" "$DEPLOY_PATH"
    printf '    %-18s %s\n' "GHCR_READ_TOKEN" "тот же PAT, что вводили выше"
    printf '    %-18s %s\n' "DEPLOY_SSH_KEY" "содержимое $DEPLOY_KEY целиком"
    printf '\n'
    hint "Скопировать приватный ключ в буфер обмена:"
    if [ "$(uname)" = "Darwin" ]; then
        hint "    pbcopy < $DEPLOY_KEY"
    else
        hint "    xclip -selection clipboard < $DEPLOY_KEY"
    fi
fi

# ====================================================== 10. Проверка

step "Проверка"

sleep 5
for url in "https://$DOMAIN/" "https://$DOMAIN/api/"; do
    printf '%-40s ' "$url"
    CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$url" || echo "---")
    case "$CODE" in
        000|---) printf '%sнет ответа%s\n' "$RED" "$N" ;;
        5*)      printf '%s%s%s\n' "$YEL" "$CODE" "$N" ;;
        *)       printf '%s%s%s\n' "$GRN" "$CODE" "$N" ;;
    esac
done

printf '\n%s━━━ Готово%s\n\n' "$B$GRN" "$N"
info "Сайт:      https://$DOMAIN"
info "MinIO:     https://console.minio.$DOMAIN"
printf '\n'
info "Дальше автодеплой работает сам: push в master собирает образы и обновляет сервер."
printf '\n'
hint "Полезное:"
hint "    ssh $SSH_ALIAS                                              # зайти на сервер"
hint "    cd $DEPLOY_PATH && docker compose -f docker-compose.prod.yaml logs -f server"
[ "$REUSE_ENV" = "no" ] && hint "    $ENV_BACKUP    # копия .env с паролями"
printf '\n'
