# Развёртывание AthleticaCRM

Разворачивание сервера автоматизировано скриптом [`scripts/setup-server.sh`](scripts/setup-server.sh).
Он запускается **на локальной машине**, спрашивает недостающие данные и доводит сервер
до рабочего состояния: Docker, пользователь деплоя, TLS-сертификат, запущенный стек
и секреты GitHub Actions для последующего автодеплоя.

## Содержание

1. [Что нужно подготовить](#1-что-нужно-подготовить)
2. [Запуск скрипта](#2-запуск-скрипта)
3. [Что получилось на сервере](#3-что-получилось-на-сервере)
4. [Автодеплой](#4-автодеплой)
5. [Переезд на другой сервер](#5-переезд-на-другой-сервер)
   - [Смена основного домена](#смена-основного-домена)
6. [Обслуживание](#6-обслуживание)
7. [Диагностика](#7-диагностика)
8. [Ручная установка без скрипта](#8-ручная-установка-без-скрипта)

---

## 1. Что нужно подготовить

### Сервер

| Компонент | Минимум |
|-----------|---------|
| ОС | Ubuntu 22.04 LTS / Debian 12 |
| RAM | 2 GB |
| CPU | 2 vCPU |
| Диск | 20 GB |
| Порты | 22 (SSH), 80 (HTTP), 443 (HTTPS) |

### SSH-ключ на сервере

Единственное действие, которое выполняется руками. Публичный ключ вашей машины должен
лежать в `authorized_keys` пользователя `root`:

```bash
ssh-copy-id -i ~/.ssh/id_ed25519.pub root@<IP сервера>
```

Скрипт начинается с проверки этого доступа и, если ключа нет, печатает команду и ждёт.

### DNS

A-записи на IP сервера. Все имена нужны для одного общего TLS-сертификата:

| Запись | Тип | Что там |
|--------|-----|---------|
| `yourdomain.com` | A | публичный лендинг |
| `www.yourdomain.com` | A | перенаправление на `yourdomain.com` |
| `app.yourdomain.com` | A | CRM: веб-клиент и API |
| `minio.yourdomain.com` | A | S3 API хранилища (ссылки на файлы) |
| `console.minio.yourdomain.com` | A | веб-консоль хранилища |

Прочие домены продукта (например, прежний основной) можно направить на тот же сервер:
nginx перенаправляет любой незнакомый хост на тот же путь в `app.yourdomain.com`.
Скрипт спросит их список и добавит в сертификат — без этого браузер покажет ошибку TLS
до перенаправления.

Распространение занимает 5–30 минут. Скрипт проверит записи через `dig` и предупредит о расхождениях.

### Токен GitHub для скачивания образов

Docker-образы приватные, серверу нужен PAT с правом `read:packages`:

1. Открыть <https://github.com/settings/tokens/new?scopes=read:packages&description=athletica-server-pull>
2. Тип — **classic** (fine-grained токены с ghcr.io работают ненадёжно), срок — `No expiration`
3. Единственный скоуп — `read:packages`
4. Скопировать значение: скрипт спросит его, и оно же уйдёт в секрет `GHCR_READ_TOKEN`

### Данные SMTP и ЮKassa

Скрипт спросит хост, логин, пароль и адрес отправителя SMTP (панель почтового провайдера)
и, опционально, `shopId` с секретным ключом ЮKassa (панель ЮKassa → Настройки → Магазин).
Приём платежей можно отложить: оставить поля пустыми и включить тестовый режим.

В панели ЮKassa (Интеграция → HTTP-уведомления) указать адрес уведомлений
`https://app.yourdomain.com/api/checkout/yookassa` и событие `payment.succeeded`.
Без этого платёж проходит, но баланс организации не пополняется. Адрес возврата после оплаты
(`YOOKASSA_RETURN_URL`) скрипт задаёт сам: `https://app.yourdomain.com/settings/org-balance/complete`.

Пароли PostgreSQL, S3-хранилища и JWT-секрет скрипт генерирует сам — вводить их не нужно.

---

## 2. Запуск скрипта

Из корня репозитория:

```bash
./scripts/setup-server.sh
```

Скрипт идемпотентен — повторный запуск безопасен: существующие пользователь, ключ,
`.env` не перезаписывается, cron-задача обновляется без дубликатов. Сертификат перевыпускается,
если изменился список имён или наступил срок обновления (`certbot --expand --keep-until-expiring`), —
так повторный запуск добавляет в него новые имена.

Порядок шагов:

| Шаг | Что происходит |
|-----|----------------|
| 1 | Проверка SSH-доступа к серверу под root |
| 2 | Основной и дополнительные домены, e-mail для Let's Encrypt, репозиторий, пользователь и каталог деплоя |
| 3 | Проверка A-записей через `dig` |
| 4 | Генерация паролей, вопросы про SMTP/ЮKassa/Sentry, GitHub PAT |
| 5 | Создание `~/.ssh/athletica_deploy` и алиаса в `~/.ssh/config` |
| 6 | Установка Docker, пользователь `deploy`, каталоги, порты в ufw |
| 7 | Копирование `docker-compose.prod.yaml`, `nginx/prod.conf.template`, `postgres/init/`, `.env` |
| 8 | Логин в ghcr.io, выпуск сертификата, файлы TLS, `pull` + `up -d`, cron автопродления |
| 9 | Запись секретов в GitHub (через `gh`, либо инструкция для ручного ввода) |
| 10 | Проверка `https://домен/`, `https://app.домен/` и `https://app.домен/api/` |

Копия `.env` с сгенерированными паролями сохраняется локально в `~/.athletica-crm/<домен>.env`
с правами 600. **Не теряйте этот файл** — восстановить пароль к базе иначе неоткуда.

---

## 3. Что получилось на сервере

```
/opt/athletica-crm/
├── .env                        секреты и параметры (600, владелец deploy)
├── docker-compose.prod.yaml    обновляется автодеплоем
├── nginx/prod.conf.template    обновляется автодеплоем
├── scripts/renew-certificates.sh  автопродление TLS, обновляется автодеплоем
├── postgres/init/              init-скрипты БД, копируются только скриптом
└── ssl/options-ssl-nginx.conf  исходник для тома letsencrypt
```

Тома Docker (имена начинаются с имени каталога проекта):

| Том | Содержимое |
|-----|------------|
| `athletica-crm_postgres_data` | база данных |
| `athletica-crm_rustfs_data` | загруженные файлы (RustFS) |
| `athletica-crm_letsencrypt` | сертификаты, `options-ssl-nginx.conf`, `ssl-dhparams.pem` |
| `athletica-crm_certbot_www` | ACME-challenge для продления |

Сервисы: `postgres`, `rustfs` (S3-хранилище файлов), `server`, `frontend` (React-клиент, `/`), `nginx`.
Сервис `certbot` в профиле `tools` — на `up` не стартует. Его запускают скрипт первичной
настройки и cron-задача через `scripts/renew-certificates.sh`.

> `docker-compose.prod.yaml` и `nginx/prod.conf.template` на сервере править нельзя —
> следующий push в `master` их перезапишет. Единственный источник правды — репозиторий.

---

## 4. Автодеплой

Workflow [`.github/workflows/docker.yml`](.github/workflows/docker.yml) на каждый push в `master`:

1. `server` — собирает `Dockerfile.server`, пушит в `ghcr.io/<repo>/server:latest`
2. `frontend` — собирает `web/Dockerfile` (проверки + сборка Vite), пушит в `ghcr.io/<repo>/frontend:latest`
3. `deploy` — после обоих: копирует compose, nginx-шаблон и скрипт автопродления на сервер,
   делает `pull`, `up -d`, перезапускает nginx, устанавливает cron и проверяет сертификат
   (при необходимости добавляет `app.`). То же делает `deploy_to_stand.sh`.

Push образов идёт под встроенным `secrets.GITHUB_TOKEN`, отдельный токен для этого не нужен.

Секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Значение |
|--------|----------|
| `DEPLOY_HOST` | IP или hostname сервера |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_PATH` | `/opt/athletica-crm` |
| `DEPLOY_SSH_KEY` | приватный ключ `~/.ssh/athletica_deploy` целиком, вместе со строками `-----BEGIN/END-----` |
| `GHCR_READ_TOKEN` | PAT с `read:packages` |

Скрипт заполняет их сам, если установлен и авторизован `gh` (`gh auth login`).
Иначе он печатает таблицу значений и ссылку на страницу секретов.

---

## 5. Переезд на другой сервер

Прогнать `./scripts/setup-server.sh` для нового сервера и обновить `DEPLOY_HOST`
(при необходимости `DEPLOY_PATH`) — сам workflow менять не нужно. Деплой всегда идёт
ровно на один хост, поэтому старый сервер просто перестанет получать обновления;
остановить его нужно вручную:

```bash
docker compose -f docker-compose.prod.yaml down
```

Данные переносятся отдельно — дамп PostgreSQL и содержимое RustFS:

```bash
# на старом сервере
docker compose -f docker-compose.prod.yaml exec -T postgres pg_dump -U athletica athletica | gzip > dump.sql.gz

# на новом
gunzip -c dump.sql.gz | docker compose -f docker-compose.prod.yaml exec -T postgres psql -U athletica athletica
```

### Смена основного домена

Основной домен задаёт только `DOMAIN` в `.env` (и производные от него значения) — код и
шаблоны менять не нужно.

1. A-записи нового домена: сам домен, `www.`, `app.`, `minio.`, `console.minio.`.
2. Прогнать `./scripts/setup-server.sh` с новым основным доменом; прежний указать в
   дополнительных доменах (`old.ru www.old.ru`), чтобы старые адреса перенаправлялись в CRM.
   Сертификат выпускается заново: его каталог называется по основному домену.
   Существующий `.env` скрипт предлагает оставить как есть (новые пароли сломали бы доступ к базе),
   поэтому в `/opt/athletica-crm/.env` поправить руками: `DOMAIN`, `MINIO_PUBLIC_ENDPOINT=https://minio.<домен>`,
   `YOOKASSA_RETURN_URL=https://app.<домен>/settings/org-balance/complete`
   (`SMTP_FROM_ADDRESS` — только когда почта для нового домена настроена у провайдера).
3. `docker compose -f docker-compose.prod.yaml up -d && docker compose -f docker-compose.prod.yaml restart nginx`.
4. Сменить адрес HTTP-уведомлений в панели ЮKassa на `https://app.<домен>/api/checkout/yookassa`.
5. Проверить: лендинг на `https://<домен>/`, вход на `https://app.<домен>/login`,
   `curl -I https://old.ru/clients` → `301` на `https://app.<домен>/clients`, тестовый платёж
   с зачислением на баланс.

---

## 6. Обслуживание

```bash
ssh athletica-crm                    # алиас, который добавил скрипт
cd /opt/athletica-crm

docker compose -f docker-compose.prod.yaml ps                    # состояние
docker compose -f docker-compose.prod.yaml logs -f server        # логи сервера
docker compose -f docker-compose.prod.yaml logs --tail=50 nginx  # логи nginx
docker compose -f docker-compose.prod.yaml restart server        # перезапуск сервиса

docker compose -f docker-compose.prod.yaml pull                  # обновить образы вручную
docker compose -f docker-compose.prod.yaml up -d

docker compose -f docker-compose.prod.yaml exec postgres psql -U athletica athletica

docker compose -f docker-compose.prod.yaml down                  # остановить (тома сохраняются)
docker compose -f docker-compose.prod.yaml down -v               # ВНИМАНИЕ: удалит данные
```

Сертификат проверяется cron-задачей пользователя `deploy` в 03:00 и 15:00 по часовому поясу
сервера. Certbot продлевает его, когда наступает срок обновления. Задача устанавливается
при первичной настройке и каждом деплое; старый cron с `certbot renew` заменяется.

```cron
0 3,15 * * * cd /opt/athletica-crm && bash scripts/renew-certificates.sh >> certificates.log 2>&1
```

Скрипт сохраняет все имена действующего сертификата, добавляет `app.<DOMAIN>` и использует
`--cert-name <DOMAIN>`, чтобы путь сертификата для nginx остался прежним. Проверка ACME
проходит через webroot `/var/www/certbot`, nginx продолжает обслуживать сайт. После успеха
проверяется конфигурация nginx и выполняется reload. Лог: `/opt/athletica-crm/certificates.log`.

На уже работающем сервере после копирования скрипта установить задачу и сразу обновить
сертификат (от пользователя `deploy`, при работающем nginx):

```bash
cd /opt/athletica-crm
bash scripts/renew-certificates.sh --install-cron
crontab -l
```

Для `app.<DOMAIN>` должна быть DNS-запись на этот сервер, а порт 80 должен быть доступен
из интернета. Проверить выпуск вхолостую через тестовый сервер Let's Encrypt:

```bash
bash scripts/renew-certificates.sh --dry-run
```

Параметры webroot, расширение имён и выбор сертификата описаны в
[руководстве Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html#changing-a-certificate-s-domains).

---

## 7. Диагностика

### `not found` при pull образа

```
failed to resolve reference "ghcr.io/owner/athletica-crm/server:latest": not found
```

В `.env` остался плейсхолдер `GITHUB_REPOSITORY=owner/athletica-crm`. Значение — реальный
репозиторий в нижнем регистре (`ghcr.io` регистрозависим). Проверить итоговые имена образов:

```bash
docker compose -f docker-compose.prod.yaml config | grep 'image:'
```

### `unauthorized` / `denied` при pull

Не выполнен `docker login ghcr.io` **под тем пользователем**, от которого запускается compose:
креды лежат в `~/.docker/config.json` конкретного пользователя, у root и `deploy` они разные.
Весь стек должен работать под `deploy` — под ним же на сервер заходит CI.

### nginx перезапускается: `open() "/etc/letsencrypt/options-ssl-nginx.conf" failed`

Файл не попал в том `letsencrypt`. Положить его туда:

```bash
docker run --rm -v athletica-crm_letsencrypt:/le -v /opt/athletica-crm/ssl:/src:ro alpine sh -c '
  cp /src/options-ssl-nginx.conf /le/options-ssl-nginx.conf
  [ -s /le/ssl-dhparams.pem ] || { apk add --no-cache openssl >/dev/null; openssl dhparam -out /le/ssl-dhparams.pem 2048; }'
docker compose -f docker-compose.prod.yaml restart nginx
```

### nginx падает: `unexpected end of file` в `options-ssl-nginx.conf:1`

В файле лежит текст `404: Not Found` — так бывает, если тянуть его `curl`'ом из репозитория
certbot по устаревшему пути (`curl -s` на 404 возвращает нулевой код и молча пишет страницу
ошибки в файл). Лечится тем же способом, что и предыдущий пункт: файл хранится в репозитории,
в `scripts/setup-server.sh`, и копируется из `ssl/options-ssl-nginx.conf`.

### Сертификат не выпускается

Let's Encrypt проверяет домен по HTTP, поэтому нужны корректные A-записи и свободный порт 80.
Перед выпуском стек останавливается (`down`), certbot поднимается в режиме `--standalone`.
Посмотреть, что уже выпущено:

```bash
docker run --rm -v athletica-crm_letsencrypt:/le alpine ls /le/live/
```

---

## 8. Ручная установка без скрипта

Если нужно повторить шаги руками (аварийное восстановление, нестандартная конфигурация),
порядок такой:

```bash
# 1. Docker (на сервере, от root)
apt-get update && apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/$(. /etc/os-release && echo "$ID")/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$(. /etc/os-release && echo "$ID") $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# 2. Пользователь и каталоги
useradd -m -s /bin/bash deploy && usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -d -o deploy -g deploy /opt/athletica-crm /opt/athletica-crm/nginx /opt/athletica-crm/ssl
# публичный ключ деплоя → /home/deploy/.ssh/authorized_keys (600, владелец deploy)

# 3. Файлы (с локальной машины)
scp docker-compose.prod.yaml        deploy@SERVER:/opt/athletica-crm/
scp nginx/prod.conf.template        deploy@SERVER:/opt/athletica-crm/nginx/
ssh deploy@SERVER 'mkdir -p /opt/athletica-crm/scripts'
scp scripts/renew-certificates.sh   deploy@SERVER:/opt/athletica-crm/scripts/
scp .env.prod.example               deploy@SERVER:/opt/athletica-crm/.env   # затем заполнить

# 4. Образы, сертификат, запуск (на сервере, от deploy)
cd /opt/athletica-crm
echo "<PAT>" | docker login ghcr.io -u <github-user> --password-stdin
docker compose -f docker-compose.prod.yaml run --rm -p 80:80 certbot certonly --standalone \
  -d yourdomain.com -d www.yourdomain.com -d app.yourdomain.com \
  -d minio.yourdomain.com -d console.minio.yourdomain.com \
  --email admin@yourdomain.com --agree-tos --no-eff-email --non-interactive
docker run --rm -v athletica-crm_letsencrypt:/le -v /opt/athletica-crm/ssl:/src:ro alpine sh -c '
  cp /src/options-ssl-nginx.conf /le/options-ssl-nginx.conf
  apk add --no-cache openssl >/dev/null && openssl dhparam -out /le/ssl-dhparams.pem 2048'
docker compose -f docker-compose.prod.yaml pull
docker compose -f docker-compose.prod.yaml up -d
bash scripts/renew-certificates.sh --install-cron
```

Содержимое `ssl/options-ssl-nginx.conf` берётся из `scripts/setup-server.sh`
(секция `SSL_OPTIONS`), полный список переменных `.env` — из [`.env.prod.example`](.env.prod.example).
