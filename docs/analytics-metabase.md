# Аналитика: Metabase

## Зачем

Список административных задач, которые нужно решать вне основного UI клиентов/тренеров:

- статистика организаций, оплат, использования сервиса
- проверка фактов (например, «было ли отправлено письмо такому-то сотруднику»)
- разовые операции (рассылка, бонусы, промокоды, блокировки) — пока через SQL, позже будет CLI-tooling

Эти задачи делятся на два класса:

- **Read-only / аналитика** — Metabase (этот документ)
- **Write-операции** — CLI-команды (отдельная задача, ещё не реализована); до их появления — `psql` под `POSTGRES_USER`

## Рассмотренные варианты

| Вариант | Почему отклонён |
|---------|-----------------|
| Собственная web-админка (KMP-модуль, JWT, отдельный поддомен) | ~33 файла, несколько недель работы, своя auth-система; non-trivial поддержка ради задач, выполняемых раз в неделю |
| psql + ad-hoc запросы напрямую | Работает, но не масштабируется на команду; нет сохранённых дашбордов; повышает риск выполнения write-запроса по ошибке |
| Retool / Forest Admin | Платно ($10–50/user); имеет смысл при workflows и формах для non-engineer — пока не нужно |
| Grafana | Сильна в метриках/time-series, слабее в произвольных SQL-отчётах |
| **Metabase** | Open source, self-hosted, drag-and-drop дашборды, поддержка SQL-вопросов, низкий порог входа |

## Принятое решение

Развернуть Metabase рядом с основным приложением и дать ему **read-only** доступ к боевой БД.

## Архитектура

```
HTTPS → nginx → metabase:3000 → postgres
                                  ├── athletica     (READ ONLY через metabase_reader)
                                  └── metabase_app  (READ/WRITE через metabase_app)
```

### Пользователи PostgreSQL

| Пользователь | БД | Права | Назначение |
|---|---|---|---|
| `metabase_app` | `metabase_app` | owner | Метаданные Metabase (дашборды, пользователи, настройки) |
| `metabase_reader` | `athletica` | SELECT на все таблицы | Подключение Metabase к основным данным |

`metabase_reader` не имеет INSERT/UPDATE/DELETE — даже Native SQL-запрос в Metabase не сможет изменить данные.

Будущие таблицы, создаваемые Liquibase, автоматически получат SELECT через `ALTER DEFAULT PRIVILEGES`.

### Сеть

- Metabase работает **внутри** docker-сети, наружу порт не публикуется
- В prod доступен только через nginx по `https://metabase.${DOMAIN}` (multi-SAN сертификат Let's Encrypt)
- В dev доступен на `http://localhost:3000`

### Хранение метаданных

Метаданные Metabase (дашборды, пользователи, настройки) хранятся в PostgreSQL (БД `metabase_app`) в том же контейнере postgres:

- единая точка бэкапа: `pg_dumpall` покрывает обе БД
- никакого дополнительного volume для метаданных
- стабильность: встроенный H2 не рекомендован Metabase для production (известные проблемы с corruption при долгой эксплуатации)

## Что осознанно НЕ включено

- **Write-операции** (бонусы, промокоды, блокировки) — отдельная задача, будет CLI
- **SSO / SAML** — встроенная email/password аутентификация Metabase пока достаточна
- **Read-replica** — основная БД выдерживает аналитические запросы; при появлении деградации — переключить `metabase_reader` на replica
- **Дашборды в Git** — Metabase не сериализует дашборды в текстовые файлы из коробки; хранятся в `metabase_app` БД, покрываются бэкапом

## Когда пересматривать решение

- Появились non-engineer пользователи, которым нужны формы или workflows → смотреть Retool/Forest или собственную admin UI
- Аналитические запросы начали влиять на производительность основной БД → выделить read-replica
- Регулярных write-операций стало >5 в неделю → строить CLI-tooling

## Переменные окружения

| Переменная | Описание |
|---|---|
| `METABASE_DB_PASSWORD` | Пароль пользователя `metabase_app` (метаданные Metabase) |
| `METABASE_READER_PASSWORD` | Пароль пользователя `metabase_reader` (read-only доступ к `athletica`) |

Генерировать: `openssl rand -hex 32`

## Операционные процедуры

### Первый деплой (свежий volume)

Init-скрипт `postgres/init/01-init-metabase.sh` выполняется автоматически при первой инициализации тома postgres. Дополнительных действий не требуется.

### Деплой на существующее окружение

Если `postgres_data` volume уже существует — init-скрипт не запустится. Применить вручную:

```bash
# Подставить реальные пароли
export METABASE_DB_PASSWORD=...
export METABASE_READER_PASSWORD=...

docker compose exec -T postgres bash <<'EOF'
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    CREATE USER metabase_app WITH PASSWORD '${METABASE_DB_PASSWORD}';
    CREATE DATABASE metabase_app OWNER metabase_app;
    CREATE USER metabase_reader WITH PASSWORD '${METABASE_READER_PASSWORD}';
    GRANT CONNECT ON DATABASE athletica TO metabase_reader;
EOSQL

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "athletica" <<-EOSQL
    GRANT USAGE ON SCHEMA public TO metabase_reader;
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase_reader;
    GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO metabase_reader;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO metabase_reader;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO metabase_reader;
EOSQL
EOF
```

### Расширение SSL-сертификата (prod, один раз)

```bash
docker compose -f docker-compose.prod.yaml run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  --expand \
  -d ${DOMAIN} \
  -d www.${DOMAIN} \
  -d minio.${DOMAIN} \
  -d console.minio.${DOMAIN} \
  -d metabase.${DOMAIN}

docker compose -f docker-compose.prod.yaml exec nginx nginx -s reload
```

Флаг `--expand` перевыпустит существующий сертификат с расширенным списком SAN, не создавая нового.

### Первичная настройка Metabase (UI)

1. Открыть `http://localhost:3000` (dev) или `https://metabase.${DOMAIN}` (prod)
2. Пройти wizard: создать admin-пользователя Metabase
3. Добавить подключение к БД:
   - **Type:** PostgreSQL
   - **Host:** `postgres`
   - **Port:** `5432`
   - **Database name:** `athletica`
   - **Username:** `metabase_reader`
   - **Password:** значение `METABASE_READER_PASSWORD`
4. Metabase просканирует схему и составит каталог таблиц

### Добавление пользователей

Admin → People → Invite (email/password). SSO не настроен.

### Бэкап

`pg_dumpall` на контейнере postgres покрывает все БД включая `metabase_app`. Если используется только `pg_dump athletica` — добавить второй вызов:

```bash
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" metabase_app > metabase_app.sql
```

### Обновление Metabase

Версия зафиксирована в docker-compose (например `v0.55.0`). Перед обновлением:

1. Проверить changelog Metabase на breaking changes в схеме метаданных
2. Сделать бэкап `metabase_app`
3. Изменить тег образа в compose, пересобрать
4. При проблемах — восстановить `metabase_app` из бэкапа
