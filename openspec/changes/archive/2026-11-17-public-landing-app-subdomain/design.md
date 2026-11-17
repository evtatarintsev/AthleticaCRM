## Context

Продукт ещё не выпущен: пользователей, сохранённых ссылок и настроенных интеграций на проде
нет, поэтому обратная совместимость адресов не требуется.

- Внешний nginx (`nginx/prod.conf.template`, подстановка `${DOMAIN}` через envsubst образа
  `nginx:alpine`) обслуживает `${DOMAIN}` и `www.${DOMAIN}`: `/api/` → `server:8080`,
  остальное → `frontend:80`. Отдельные `server` для `minio.` и `console.minio.`.
- Образ `frontend` (`web/Dockerfile`) — собранная SPA во внутреннем nginx (`web/nginx.conf`).
  SPA работает с корня (`BASE_PATH = ""`), cookie сессии host-only, `SameSite=Strict`.
- Один сертификат Let's Encrypt с SAN-списком, путь `/etc/letsencrypt/live/${DOMAIN}/`.
  Список имён задаёт `scripts/setup-server.sh` (`CERT_DOMAINS`); выпуск — standalone,
  продление — cron.
- Автодеплой (`.github/workflows/docker.yml`) копирует на сервер только
  `docker-compose.prod.yaml` и `nginx/prod.conf.template` и тянет образы.
- Абсолютные адреса продукта в коде есть только в `yookassa.returnUrl`
  (по умолчанию `https://athletictools.ru/payment/complete` — такого маршрута в SPA нет,
  реальная страница — `/settings/org-balance/complete`). Сервер шлёт лишь письма клубов
  их клиентам, ссылок на продукт в них нет. Telegram работает через polling, вебхуков нет.
- Вебхук ЮKassa — `POST /checkout/yookassa` (вне `/api`, см. `Application.kt`), но внешний
  nginx проксирует на сервер только `/api/`: сейчас уведомления попадают во внутренний nginx
  SPA и до Ktor не доходят.

## Goals / Non-Goals

**Goals:**
- Раскладка хостов из `public-site` целиком в шаблоне nginx, с одной переменной `DOMAIN`.
- Лендинг выкатывается тем же пайплайном, что и SPA, без новых сервисов и шагов копирования.
- React-приложение и сервер не меняются (кроме значения по умолчанию `returnUrl`).

**Non-Goals:**
- Дизайн и содержание лендинга, локализация лендинга (первая итерация — только русский).
- Перевод почты на новый домен (SPF/DKIM для `athleticacrm.ru`).
- Пользовательские домены клубов.

## Decisions

### CRM на поддомене `app.`, а не на пути `/app`

Поддомен разводит лендинг и SPA полностью: разные кэши и заголовки, запрет индексации
одним заголовком на весь хост, нет пересечения путей (`/login`, `/pricing`). SPA, API и
cookie не меняются — `BASE_PATH` остаётся пустым. Путь `/app` потребовал бы
`BASE_PATH="/app"`, сдвига пути refresh-cookie и перенаправлений со всех старых адресов.
`/lk` и `/crm` отвергнуты: «ЛК» оставляем под будущий кабинет клиента клуба, `/crm` на
`athleticacrm.ru` — тавтология.

### Лендинг — статичный HTML в образе `frontend`, второй `server` внутреннего nginx

`web/landing/index.html` (+ favicon) копируется в образ в `/usr/share/nginx/landing`,
внутренний nginx отдаёт его на отдельном порту (`8081`); внешний nginx проксирует
основной домен на `frontend:8081`. Лендинг версионируется вместе с кодом и выкатывается
текущим автодеплоем.

Альтернативы: отдельный образ/сервис `landing` — лишняя сборка в CI ради одного файла;
файлы в томе внешнего nginx — пришлось бы добавлять копирование в автодеплой,
`deploy_to_stand.sh` и `setup-server.sh`; страница внутри React-приложения — тянет весь
бандл, зависит от SPA и мешает индексации.

Строки лендинга пишутся прямо в HTML: правило словарей `i18n` относится к React-приложению,
а у лендинга пока один язык. Когда лендинг станет отдельным изменением, этот выбор
пересматривается вместе с его стеком.

### Ссылки лендинга относительные, nginx перенаправляет на `app.`

Ссылки «Войти» / «Регистрация» — `/login` и `/sign-up`. Внешний nginx на основном домене
отвечает на них `302` на `https://app.${DOMAIN}$request_uri`. Статичному HTML не нужно знать
домен (нет шаблонизации и JS), а набранный руками `athleticacrm.ru/login` тоже работает.
`302`, а не `301`: адреса на основном домене могут позже понадобиться лендингу.

### Прочие домены — `default_server`

Блок `listen 443 ssl default_server` без `server_name` отвечает `301` на
`https://app.${DOMAIN}$request_uri`. Старый домен и любые будущие домены перенаправляются без
отдельной переменной окружения и без перечисления имён в шаблоне (пустой список имён в
`server_name` nginx не принимает). Порт 80 — тоже `default_server`: ACME-challenge и `301` на
HTTPS того же хоста. Обязательное условие: имена прочих доменов есть в SAN сертификата,
иначе браузер покажет ошибку до перенаправления.

### Вебхук ЮKassa переезжает под `/api`

Маршрут `checkoutRoutes` регистрируется внутри `route("/api")`, но вне
`authenticate("auth-jwt")`: адрес становится `POST /api/checkout/yookassa`. Тогда весь
серверный трафик идёт по одному правилу nginx (`/api/` → Ktor) в prod и dev, и отдельный
`location` для вебхука не нужен. Альтернатива — `location = /checkout/yookassa` в nginx —
оставляет второй путь к серверу, о котором легко забыть (так и случилось). Адрес уведомлений
в кабинете ЮKassa — `https://app.${DOMAIN}/api/checkout/yookassa`.

### Перенаправления с `/web/...` не нужны

Спека `web-client/migration` требовала `301` с `/web/...`, но в nginx их уже нет, а старых
ссылок до релиза нет. Требование убирается, вместе с упоминанием в KDoc `vite.config.ts`.

### Запрет индексации — заголовок на хосте `app.`

`add_header X-Robots-Tag "noindex, nofollow" always;` в `server` для `app.${DOMAIN}`
покрывает и HTML, и API, не требуя `robots.txt` в SPA.

### Список имён сертификата

`CERT_DOMAINS` в `setup-server.sh` дополняется `app.$DOMAIN` и необязательным списком
дополнительных доменов (вопрос скрипта, например `athletictools.ru www.athletictools.ru`).
Каждое имя — это DNS A-запись на сервер. На существующем сервере сертификат перевыпускается
вручную по инструкции в `DEPLOY.md` (`certbot certonly --standalone --expand` или, при смене
`DOMAIN`, новый выпуск — каталог `live/` называется по первому имени).

### Dev

`nginx/dev.conf`: `athletica.crm` отдаёт `web/landing/` (том в dev nginx) и перенаправляет
`/login`, `/sign-up`; `app.athletica.crm` → Vite и Ktor, как сейчас `athletica.crm`.
В `gen-dev-cert.sh` — SAN `app.athletica.crm`, в `vite.config.ts` — `allowedHosts`,
в README — строка `/etc/hosts`.

## Risks / Trade-offs

- [Сертификат не покрывает `athletictools.ru` → ошибка TLS вместо перенаправления]
  → Включить `athletictools.ru` и `www.` в SAN при выпуске; проверка
  `curl -I https://athletictools.ru/clients`.
- [Адрес уведомлений в кабинете ЮKassa не совпадает с маршрутом → баланс не пополняется]
  → Шаг миграции и пункт `DEPLOY.md`; проверка тестовым платежом с зачислением.
- [Письма с `noreply@athletictools.ru` после смены домена]
  → На проде `SMTP_FROM_ADDRESS` остаётся прежним, пока почта для нового домена не настроена.

## Migration Plan

Переключение одним шагом, без промежуточного состояния на старом домене:

1. DNS: A-записи `athleticacrm.ru`, `www.`, `app.`, `minio.`, `console.minio.` на сервер.
2. Выпустить сертификат на `athleticacrm.ru` с SAN: `www.`, `app.`, `minio.`, `console.minio.`
   нового домена, `athletictools.ru`, `www.athletictools.ru`.
3. В `.env` на сервере: `DOMAIN=athleticacrm.ru`, `MINIO_PUBLIC_ENDPOINT`,
   `YOOKASSA_RETURN_URL=https://app.athleticacrm.ru/settings/org-balance/complete`;
   выкатить код, `docker compose up -d`.
4. В кабинете ЮKassa указать адрес HTTP-уведомлений
   `https://app.athleticacrm.ru/api/checkout/yookassa`.
5. Проверка: лендинг, вход, тестовый платёж с зачислением на баланс, перенаправление
   с `athletictools.ru`.
