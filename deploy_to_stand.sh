#!/usr/bin/env bash
#
# Быстрая выкатка на уже настроенный стенд.
#
#   ./deploy_to_stand.sh <ip> [опции]
#
# Делает то же, что workflow .github/workflows/docker.yml, но без GitHub:
# собирает артефакты локальным Gradle (тёплый кэш → быстро), пакует их
# в те же образы, что собирает CI, заливает их на сервер через ssh
# и перезапускает стек. Реестр ghcr.io не участвует.
#
# Предполагается, что стенд уже поднят ./scripts/setup-server.sh:
# на нём есть $DEPLOY_PATH/.env, тома с данными и сертификат.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

DEPLOY_USER="${DEPLOY_USER:-deploy}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/athletica-crm}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/athletica_deploy}"
COMPOSE_FILE="docker-compose.prod.yaml"
SKIP_GRADLE=0
ONLY=""

BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; OFF=$'\033[0m'
info() { printf '%s==>%s %s\n' "$BOLD" "$OFF" "$1"; }
hint() { printf '%s    %s%s\n' "$DIM" "$1" "$OFF"; }
warn() { printf '%s !%s %s\n' "$YELLOW" "$OFF" "$1"; }
die()  { printf '%sОшибка:%s %s\n' "$RED" "$OFF" "$1" >&2; exit 1; }

usage() {
    cat <<'USAGE'
Использование: ./deploy_to_stand.sh <ip|hostname> [опции]

Опции:
  -u, --user USER     пользователь на сервере (по умолчанию deploy)
  -p, --path PATH     каталог проекта на сервере (по умолчанию /opt/athletica-crm)
  -i, --key FILE      приватный ssh-ключ (по умолчанию ~/.ssh/athletica_deploy)
      --only server   выкатить только сервер
      --only web      выкатить только фронтенд
      --skip-gradle   не пересобирать артефакты, взять готовые из build/
  -h, --help          эта справка

Переменные окружения DEPLOY_USER, DEPLOY_PATH, SSH_KEY работают так же, как опции.
USAGE
}

SERVER_HOST=""
while [ $# -gt 0 ]; do
    case "$1" in
        -u|--user) DEPLOY_USER="$2"; shift 2 ;;
        -p|--path) DEPLOY_PATH="$2"; shift 2 ;;
        -i|--key)  SSH_KEY="$2"; shift 2 ;;
        --only)    ONLY="$2"; shift 2 ;;
        --skip-gradle) SKIP_GRADLE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        -*) usage; die "неизвестная опция $1" ;;
        *)  [ -z "$SERVER_HOST" ] || die "лишний аргумент $1"; SERVER_HOST="$1"; shift ;;
    esac
done

[ -n "$SERVER_HOST" ] || { usage; exit 1; }
case "$ONLY" in ""|server|web) ;; *) die "--only принимает server или web" ;; esac

DEPLOY_SERVER=1; DEPLOY_WEB=1
[ "$ONLY" = "web" ] && DEPLOY_SERVER=0
[ "$ONLY" = "server" ] && DEPLOY_WEB=0

command -v docker >/dev/null || die "не найден docker"
docker info >/dev/null 2>&1 || die "демон docker не запущен"

SSH_OPTS=(-o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new)
[ -f "$SSH_KEY" ] && SSH_OPTS+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)
REMOTE="$DEPLOY_USER@$SERVER_HOST"
ssh_do() { ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"; }

STARTED_AT=$SECONDS

# ── 1. Проверка стенда ────────────────────────────────────────────────────────
info "Проверяю стенд $REMOTE"
ssh_do true 2>/dev/null || die "нет ssh-доступа к $REMOTE (ключ $SSH_KEY)"
ssh_do "test -f '$DEPLOY_PATH/.env'" \
    || die "на сервере нет $DEPLOY_PATH/.env — стенд не настроен, сначала ./scripts/setup-server.sh"
ssh_do "docker info >/dev/null 2>&1" || die "на сервере недоступен docker под пользователем $DEPLOY_USER"

# Имена образов в compose завязаны на GITHUB_REPOSITORY из .env стенда:
# собранные локально образы должны получить ровно эти теги.
REPO=$(ssh_do "grep -E '^GITHUB_REPOSITORY=' '$DEPLOY_PATH/.env' | tail -1 | cut -d= -f2-" | tr -d '"'"'"' \r')
[ -n "$REPO" ] || die "в $DEPLOY_PATH/.env не задан GITHUB_REPOSITORY"
IMAGE_SERVER="ghcr.io/$REPO/server:latest"
IMAGE_WEB="ghcr.io/$REPO/web:latest"

REMOTE_ARCH=$(ssh_do "uname -m" | tr -d '\r')
case "$REMOTE_ARCH" in
    x86_64|amd64)   PLATFORM="linux/amd64" ;;
    aarch64|arm64)  PLATFORM="linux/arm64" ;;
    *) die "неизвестная архитектура сервера: $REMOTE_ARCH" ;;
esac
hint "образы: $IMAGE_SERVER, $IMAGE_WEB ($PLATFORM)"

# ── 2. Локальная сборка артефактов ────────────────────────────────────────────
JAR="server/build/libs/athletica.jar"
WEB_DIST="composeApp/build/dist/wasmJs/productionExecutable"

if [ "$SKIP_GRADLE" = "1" ]; then
    warn "Gradle пропущен, использую то, что уже лежит в build/"
else
    TASKS=()
    [ "$DEPLOY_SERVER" = "1" ] && TASKS+=(":server:shadowJar")
    [ "$DEPLOY_WEB" = "1" ] && TASKS+=(":composeApp:wasmJsBrowserDistribution")
    info "Собираю артефакты: ${TASKS[*]}"
    ./gradlew "${TASKS[@]}"
fi

[ "$DEPLOY_SERVER" = "0" ] || [ -f "$JAR" ] || die "нет $JAR"
[ "$DEPLOY_WEB" = "0" ] || [ -d "$WEB_DIST" ] || die "нет $WEB_DIST"

# ── 3. Образы из готовых артефактов ───────────────────────────────────────────
# Dockerfile.server и Dockerfile.web собирают проект внутри контейнера — это
# долго. Здесь берём те же базовые образы и кладём в них уже собранное.
CONTEXT=$(mktemp -d)
BUILD_LOG=$(mktemp)
trap 'rm -rf "$CONTEXT" "$BUILD_LOG"' EXIT

# Тихая сборка: вывод docker показывается только если сборка провалилась
# и повторять её уже нечем (у образа сервера есть запасная база).
build_image() {
    local tag="$1" dockerfile="$2" quiet="${3:-0}"
    if docker build --platform "$PLATFORM" -f "$dockerfile" -t "$tag" "$CONTEXT" \
        >"$BUILD_LOG" 2>&1; then
        return 0
    fi
    [ "$quiet" = "1" ] && return 1
    cat "$BUILD_LOG" >&2
    die "не собрался образ $tag"
}

# Версия JDK, под которую собран jar: major в class-файле минус 44
# (61 → 17, 69 → 25). CI собирает внутри eclipse-temurin:17-jdk и всегда
# получает 17, локальный Gradle — под тем JDK, что стоит у разработчика
# (см. .sdkmanrc). Базовый образ подбирается под байткод, иначе JRE падает
# с UnsupportedClassVersionError на старте.
jar_jdk() {
    local major
    major=$(unzip -p "$1" org/athletica/crm/ApplicationKt.class 2>/dev/null |
        od -An -tu1 -j7 -N1 | tr -d ' \n')
    [ -n "$major" ] || die "не удалось прочитать версию байткода из $1"
    echo $((major - 44))
}

if [ "$DEPLOY_SERVER" = "1" ]; then
    JDK=$(jar_jdk "$JAR")
    info "Собираю образ сервера (байткод Java $JDK)"
    if [ "$JDK" != "17" ]; then
        warn "Dockerfile.server в CI использует JRE 17, локальный jar собран под Java $JDK."
        hint "беру eclipse-temurin:$JDK-jre; чтобы стенд и прод были на одном JRE,"
        hint "закрепите toolchain в Gradle или соберите под JDK 17 (sdk use java 17…)"
    fi
    cp "$JAR" "$CONTEXT/athletica.jar"
    write_server_dockerfile() {
        cat > "$CONTEXT/server.Dockerfile" <<DOCKERFILE
FROM eclipse-temurin:$JDK-jre$1
WORKDIR /app
COPY athletica.jar athletica.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "athletica.jar"]
CMD ["serve"]
DOCKERFILE
    }
    # Для части версий Temurin alpine-образа нет под нужную архитектуру —
    # тогда откатываемся на обычный (glibc) вариант.
    write_server_dockerfile "-alpine"
    if ! build_image "$IMAGE_SERVER" "$CONTEXT/server.Dockerfile" 1; then
        warn "eclipse-temurin:$JDK-jre-alpine недоступен для $PLATFORM, беру eclipse-temurin:$JDK-jre"
        write_server_dockerfile ""
        build_image "$IMAGE_SERVER" "$CONTEXT/server.Dockerfile"
    fi
fi

if [ "$DEPLOY_WEB" = "1" ]; then
    info "Собираю образ фронтенда"
    mkdir -p "$CONTEXT/web"
    cp -R "$WEB_DIST/." "$CONTEXT/web/"
    cp nginx.conf "$CONTEXT/nginx.conf"
    cat > "$CONTEXT/web.Dockerfile" <<'DOCKERFILE'
FROM nginx:stable-alpine
COPY web/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
DOCKERFILE
    build_image "$IMAGE_WEB" "$CONTEXT/web.Dockerfile"
fi

# ── 4. Перенос образов на стенд ───────────────────────────────────────────────
IMAGES=()
[ "$DEPLOY_SERVER" = "1" ] && IMAGES+=("$IMAGE_SERVER")
[ "$DEPLOY_WEB" = "1" ] && IMAGES+=("$IMAGE_WEB")

info "Заливаю образы на $SERVER_HOST"
hint "$(docker image inspect "${IMAGES[@]}" --format '{{.RepoTags}} {{.Size}} байт' | tr '\n' ' ')"
docker save "${IMAGES[@]}" | gzip -1 | ssh "${SSH_OPTS[@]}" "$REMOTE" "gunzip | docker load"

# ── 5. Инфраструктурные файлы ─────────────────────────────────────────────────
info "Копирую $COMPOSE_FILE и nginx/prod.conf.template"
scp "${SSH_OPTS[@]}" -q "$COMPOSE_FILE" "$REMOTE:$DEPLOY_PATH/$COMPOSE_FILE"
ssh_do "mkdir -p '$DEPLOY_PATH/nginx'"
scp "${SSH_OPTS[@]}" -q nginx/prod.conf.template "$REMOTE:$DEPLOY_PATH/nginx/prod.conf.template"

# ── 6. Перезапуск стека ───────────────────────────────────────────────────────
# Без `pull`: образы уже лежат на сервере, иначе ghcr.io перетрёт их своими.
info "Перезапускаю стек"
ssh_do "set -e
cd '$DEPLOY_PATH'
docker compose -f $COMPOSE_FILE up -d
docker compose -f $COMPOSE_FILE restart nginx
docker image prune -f >/dev/null
docker compose -f $COMPOSE_FILE ps"

DOMAIN=$(ssh_do "grep -E '^DOMAIN=' '$DEPLOY_PATH/.env' | tail -1 | cut -d= -f2-" | tr -d '"'"'"' \r')
if [ -n "$DOMAIN" ]; then
    printf 'Проверяю https://%s/api/ … ' "$DOMAIN"
    CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "https://$DOMAIN/api/" || true)
    case "$CODE" in
        2*|3*|4*) printf '%sHTTP %s%s\n' "$GREEN" "$CODE" "$OFF" ;;
        *) printf '%sHTTP %s%s\n' "$YELLOW" "${CODE:-нет ответа}" "$OFF"
           hint "логи: ssh $REMOTE 'cd $DEPLOY_PATH && docker compose -f $COMPOSE_FILE logs --tail=50 server'" ;;
    esac
fi

printf '%sГотово за %d с.%s\n' "$GREEN" "$((SECONDS - STARTED_AT))" "$OFF"
