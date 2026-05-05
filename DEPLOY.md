# Руководство по деплою AthleticaCRM на production

## Содержание

1. [Требования](#1-требования)
2. [Подготовка сервера](#2-подготовка-сервера)
3. [Настройка DNS](#3-настройка-dns)
4. [Деплой файлов проекта](#4-деплой-файлов-проекта)
5. [Настройка переменных окружения](#5-настройка-переменных-окружения)
6. [Первый запуск и TLS-сертификат](#6-первый-запуск-и-tls-сертификат)
7. [Настройка GitHub Actions](#7-настройка-github-actions)
8. [Автоматическое обновление сертификатов](#8-автоматическое-обновление-сертификатов)
9. [Проверка деплоя](#9-проверка-деплоя)
10. [Полезные команды](#10-полезные-команды)

---

## 1. Требования

| Компонент | Минимум |
|-----------|---------|
| ОС | Ubuntu 22.04 LTS / Debian 12 |
| RAM | 2 GB |
| CPU | 2 vCPU |
| Диск | 20 GB |
| Открытые порты | 22 (SSH), 80 (HTTP), 443 (HTTPS) |

---

## 2. Подготовка сервера

Все команды выполняются от `root` или через `sudo`.

### 2.1 Установка Docker

```bash
# Удалить старые версии (если есть)
apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

# Установить зависимости
apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release

# Добавить официальный GPG-ключ Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/$(. /etc/os-release && echo "$ID")/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

# Добавить репозиторий Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/$(. /etc/os-release && echo "$ID") \
  $(lsb_release -cs) stable" \
  | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Установить Docker Engine и Compose plugin
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Включить автозапуск Docker
systemctl enable --now docker

# Проверить версии
docker --version
docker compose version
```

### 2.2 Создание пользователя для деплоя

```bash
# Создать пользователя deploy (без пароля, только SSH-ключ)
useradd -m -s /bin/bash deploy

# Добавить в группу docker (чтобы мог запускать docker без sudo)
usermod -aG docker deploy

# Создать директорию SSH
mkdir -p /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

### 2.3 Добавить SSH-ключ деплоя

На **локальной машине** сгенерировать ключевую пару специально для деплоя:

```bash
# Генерировать ключ (без passphrase)
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/athletica_deploy -N ""

# Вывести публичный ключ — его нужно скопировать на сервер
cat ~/.ssh/athletica_deploy.pub
```

На **сервере** добавить публичный ключ:

```bash
# Вставить содержимое athletica_deploy.pub
echo "ssh-ed25519 AAAA... github-actions-deploy" >> /home/deploy/.ssh/authorized_keys
```

Приватный ключ (`~/.ssh/athletica_deploy`) будет использоваться как GitHub Secret `DEPLOY_SSH_KEY`.

### 2.4 Создать директорию проекта

```bash
mkdir -p /opt/athletica-crm/nginx
chown -R deploy:deploy /opt/athletica-crm
```

---

## 3. Настройка DNS

Создать A-записи для вашего домена, указывающие на IP сервера:

| Запись | Тип | Значение |
|--------|-----|----------|
| `yourdomain.com` | A | `<IP сервера>` |
| `www.yourdomain.com` | A | `<IP сервера>` |
| `minio.yourdomain.com` | A | `<IP сервера>` |
| `console.minio.yourdomain.com` | A | `<IP сервера>` |

Дождаться распространения DNS (обычно 5–30 минут). Проверить:

```bash
dig +short yourdomain.com
dig +short minio.yourdomain.com
```

---

## 4. Деплой файлов проекта

Скопировать необходимые файлы из репозитория на сервер. Выполняется **с локальной машины** из корня репозитория:

```bash
SERVER=deploy@<IP сервера>
DEPLOY_PATH=/opt/athletica-crm

# Скопировать docker-compose и nginx-конфиг
scp docker-compose.prod.yaml    $SERVER:$DEPLOY_PATH/docker-compose.prod.yaml
scp nginx/prod.conf.template    $SERVER:$DEPLOY_PATH/nginx/prod.conf.template
scp .env.prod.example           $SERVER:$DEPLOY_PATH/.env.prod.example
```

> **После первого деплоя** обновление этих файлов происходит автоматически через GitHub Actions.
> Но если вы вносите изменения в `docker-compose.prod.yaml` или `nginx/prod.conf.template`,
> нужно повторить `scp` вручную — GitHub Actions копирует только Docker-образы, не файлы.

---

## 5. Настройка переменных окружения

На **сервере** от пользователя `deploy`:

```bash
su - deploy
cd /opt/athletica-crm

# Создать .env на основе шаблона
cp .env.prod.example .env
nano .env
```

Заполнить все поля в `.env`:

```env
# Репозиторий GitHub (owner/repo, строчные буквы)
GITHUB_REPOSITORY=myorg/athletikacrm

# Домен (без https://, без слеша)
DOMAIN=yourdomain.com

# PostgreSQL — задать надёжный пароль
POSTGRES_USER=athletica
POSTGRES_PASSWORD=<надёжный пароль>

# JWT — случайная строка минимум 64 символа
# Сгенерировать: openssl rand -hex 32
JWT_SECRET=<случайная строка>

# MinIO
MINIO_ACCESS_KEY=<имя пользователя MinIO>
MINIO_SECRET_KEY=<пароль MinIO, минимум 8 символов>
MINIO_BUCKET=athletica-crm

# SMTP
SMTP_HOST=smtp.yourprovider.com
SMTP_USERNAME=noreply@yourdomain.com
SMTP_PASSWORD=<пароль SMTP>
SMTP_FROM_ADDRESS=noreply@yourdomain.com
```

---

## 6. Первый запуск и TLS-сертификат

Выполняется **на сервере** от пользователя `deploy`, в директории `/opt/athletica-crm`.

### Шаг 1 — Подготовить файлы для Certbot

```bash
# Скачать рекомендованные параметры SSL от Certbot
curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf \
  | sudo tee /etc/letsencrypt/options-ssl-nginx.conf > /dev/null

# Сгенерировать DH-параметры (может занять 1–2 минуты)
sudo openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
```

### Шаг 2 — Запустить nginx с временным HTTP-конфигом

Nginx не стартует, если сертификаты ещё не получены (конфиг ссылается на несуществующие файлы).
Временно заменить конфиг на HTTP-only:

```bash
# Создать временный конфиг только для ACME challenge
docker compose -f docker-compose.prod.yaml run --rm \
  -v /opt/athletica-crm/nginx:/etc/nginx/templates \
  --entrypoint sh nginx -c "
cat > /etc/nginx/conf.d/default.conf <<'EOF'
server {
    listen 80;
    server_name _;
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / {
        return 200 'ok';
    }
}
EOF
nginx -g 'daemon off;'
"
```

Проще: запустить nginx отдельно:

```bash
docker compose -f docker-compose.prod.yaml up -d nginx
```

Если nginx не стартует из-за отсутствия сертификатов — временно закомментировать `443`-блоки в `nginx/prod.conf.template`, запустить, получить сертификат, вернуть конфиг.

### Шаг 3 — Получить TLS-сертификат

```bash
# Заменить yourdomain.com и admin@yourdomain.com на реальные значения
docker compose -f docker-compose.prod.yaml run --rm certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  -d yourdomain.com \
  -d www.yourdomain.com \
  -d minio.yourdomain.com \
  -d console.minio.yourdomain.com \
  --email admin@yourdomain.com \
  --agree-tos \
  --no-eff-email
```

При успехе сертификаты появятся в `/etc/letsencrypt/live/yourdomain.com/`.

### Шаг 4 — Запустить весь стек

```bash
docker compose -f docker-compose.prod.yaml up -d
```

Проверить статус:

```bash
docker compose -f docker-compose.prod.yaml ps
```

Все сервисы должны быть в статусе `Up`.

---

## 7. Настройка GitHub Actions

### 7.1 Создать GitHub PAT для pull образов

1. Открыть GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Нажать **Generate new token (classic)**
3. Выбрать scope: `read:packages`
4. Скопировать токен — он будет использован как `GHCR_READ_TOKEN`

### 7.2 Добавить секреты в репозиторий

Открыть репозиторий на GitHub → Settings → Secrets and variables → Actions → **New repository secret**

Добавить следующие секреты:

| Имя секрета | Значение |
|-------------|----------|
| `DEPLOY_HOST` | IP-адрес или hostname сервера |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | Содержимое файла `~/.ssh/athletica_deploy` (приватный ключ) |
| `DEPLOY_PATH` | `/opt/athletica-crm` |
| `GHCR_READ_TOKEN` | PAT с правом `read:packages` (шаг 7.1) |

### 7.3 Как добавить секрет

1. Нажать **New repository secret**
2. В поле **Name** — имя из таблицы выше
3. В поле **Secret** — соответствующее значение
4. Нажать **Add secret**

### 7.4 Проверить workflow

После добавления всех секретов сделать любой коммит в `master`.
В разделе **Actions** репозитория должен появиться запуск с тремя jobs:
- `server` — сборка и push образа Ktor-сервера
- `web` — сборка и push образа SPA
- `deploy` — SSH-деплой на сервер (запускается только после успешного завершения обоих)

---

## 8. Автоматическое обновление сертификатов

Let's Encrypt выдаёт сертификаты на 90 дней. Настроить автообновление через cron **на сервере**:

```bash
# Открыть crontab для пользователя deploy
crontab -e
```

Добавить строку:

```cron
0 3 * * * cd /opt/athletica-crm && docker compose -f docker-compose.prod.yaml run --rm certbot renew --quiet && docker compose -f docker-compose.prod.yaml exec nginx nginx -s reload
```

Сертификат будет проверяться каждую ночь в 03:00; обновление происходит только когда до истечения остаётся менее 30 дней.

---

## 9. Проверка деплоя

```bash
# Все контейнеры запущены
docker compose -f docker-compose.prod.yaml ps

# API отвечает (без /api/health можно заменить любым существующим эндпоинтом)
curl -I https://yourdomain.com/api/

# SPA отдаётся
curl -I https://yourdomain.com/

# Лог сервера (последние 50 строк)
docker compose -f docker-compose.prod.yaml logs server --tail=50

# Лог nginx
docker compose -f docker-compose.prod.yaml logs nginx --tail=50
```

---

## 10. Полезные команды

```bash
# Перезапустить все сервисы
docker compose -f docker-compose.prod.yaml restart

# Перезапустить один сервис
docker compose -f docker-compose.prod.yaml restart server

# Принудительно обновить образы вручную
docker compose -f docker-compose.prod.yaml pull
docker compose -f docker-compose.prod.yaml up -d

# Посмотреть логи в реальном времени
docker compose -f docker-compose.prod.yaml logs -f server

# Остановить всё (данные в volumes сохраняются)
docker compose -f docker-compose.prod.yaml down

# Полная очистка (ВНИМАНИЕ: удаляет данные PostgreSQL и MinIO)
docker compose -f docker-compose.prod.yaml down -v

# Войти в контейнер сервера
docker compose -f docker-compose.prod.yaml exec server sh

# Войти в PostgreSQL
docker compose -f docker-compose.prod.yaml exec postgres psql -U athletica athletica
```
