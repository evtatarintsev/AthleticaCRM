# Tasks

## 0. Вебхук ЮKassa под /api

- [x] 0.1 В `Application.kt` перенести вызов `checkoutRoutes(...)` внутрь `route("/api")`, вне `authenticate("auth-jwt")`; KDoc в `CheckoutRoutes.kt` — про адрес `/api/checkout/yookassa`. Проверка: route-тест (по образцу `HttpAuthHeaderTest`) — `POST /api/checkout/yookassa` без авторизации с неверной подписью отвечает не `401` и не `404`; старый `/checkout/yookassa` — `404`; `./gradlew build` и `./gradlew ktlintFormat` проходят

## 1. Лендинг в образе frontend

- [x] 1.1 Создать `web/landing/index.html`: название продукта, ссылки «Войти» (`/login`) и «Регистрация» (`/sign-up`), без внешних скриптов и запросов к API; favicon переиспользовать из `web/public/favicon.svg`. Проверка: `npm run format && npm run check` в `web/` проходит, файл открывается в браузере локально
- [x] 1.2 В `web/nginx.conf` добавить второй `server` на порту `8081` с корнем `/usr/share/nginx/landing` (`try_files $uri $uri/ =404`), в `web/Dockerfile` — `COPY landing/ /usr/share/nginx/landing/` и `EXPOSE 8081`. Проверка: `docker build web` и `curl localhost:8081/` в запущенном контейнере возвращают лендинг, `curl localhost:80/clients` — `index.html` SPA
- [x] 1.3 Обновить `web/README.md`: где лендинг, как он отдаётся, почему строки не из словарей i18n. Проверка: раздел есть и ссылается на `web/landing/`

## 2. Прод: шаблон nginx

- [x] 2.1 В `nginx/prod.conf.template` заменить `server` основного домена на `server` для `app.${DOMAIN}`: `/api/` → `server:8080`, `/` → `frontend:80`, `add_header X-Robots-Tag "noindex, nofollow" always`. Проверка: `docker run --rm -e DOMAIN=example.ru -v $PWD/nginx/prod.conf.template:/etc/nginx/templates/default.conf.template:ro nginx:alpine nginx -T` разбирает конфиг (с заглушками сертификатов) — либо проверка 2.5
- [x] 2.2 Добавить `server` для `${DOMAIN}`: `/` → `frontend:8081`; `location = /login` и `location = /sign-up` → `302` на `https://app.${DOMAIN}$request_uri`. Проверка: сценарии «Переход ко входу», «Вход по адресу основного домена» из `public-site` (через 2.5)
- [x] 2.3 Добавить `server` для `www.${DOMAIN}` → `301` на `https://${DOMAIN}$request_uri`; `listen 443 ssl default_server` без имени → `301` на `https://app.${DOMAIN}$request_uri`; блок порта 80 сделать `default_server` (ACME + `301` на HTTPS того же хоста). Проверка: сценарии «Старая закладка», «Адрес с www», «Открытие по HTTP» (через 2.5)
- [x] 2.4 Значение по умолчанию `yookassa.returnUrl` в `application.conf` и `YOOKASSA_RETURN_URL` в `.env.prod.example` → `https://app.<домен>/settings/org-balance/complete`; в `.env.prod.example` пример `DOMAIN=athleticacrm.ru` и производные (`MINIO_PUBLIC_ENDPOINT`, `SMTP_*`). Новых переменных нет — `docker-compose.prod.yaml` не меняется. Проверка: `./gradlew build` и `./gradlew ktlintFormat` проходят, `grep -rn athletictools` не находит ничего, кроме документации о переезде
- [x] 2.5 Проверить шаблон локально: `docker compose -f docker-compose.prod.yaml` со самоподписанным сертификатом на `example.test` (или эквивалентный стенд), прогнать `curl -kI` по всем сценариям `public-site` (лендинг, `app.`, `/login` с query, `www.`, чужой Host, HTTP, `X-Robots-Tag` только на `app.`, `POST /api/checkout/yookassa` доходит до Ktor). Проверка: все ответы совпадают со сценариями спеки

## 3. Скрипты развёртывания и документация

- [x] 3.1 `scripts/setup-server.sh`: `app.$DOMAIN` в `CERT_DOMAINS`, необязательный вопрос про дополнительные домены (добавляются в SAN), `YOOKASSA_RETURN_URL` на `app.`, проверка доступности `https://$DOMAIN/` и `https://app.$DOMAIN/api/`, итоговые ссылки. Проверка: `bash -n scripts/setup-server.sh`, `shellcheck` при наличии
- [x] 3.2 `deploy_to_stand.sh`: проверка доступности на `https://app.$DOMAIN/api/`. Проверка: `bash -n deploy_to_stand.sh`
- [x] 3.3 `DEPLOY.md`: DNS-записи (`app.`, дополнительные домены), перевыпуск сертификата с `--expand` на существующем сервере, адрес вебхука ЮKassa `https://app.<домен>/api/checkout/yookassa`, раздел «Смена основного домена» (шаги из Migration Plan в `design.md`, откат). Проверка: команды из документа совпадают с `setup-server.sh`

## 4. Dev-окружение

- [x] 4.1 `nginx/gen-dev-cert.sh`: SAN `app.athletica.crm`; `nginx/dev.conf`: `app.athletica.crm` → Vite + Ktor (`/api/`), `athletica.crm` → лендинг из тома + `302` для `/login`, `/sign-up`; `docker-compose.dev.yaml`: том `./web/landing:/usr/share/nginx/landing:ro`; `web/vite.config.ts`: `allowedHosts` с `app.athletica.crm`, из KDoc убрать упоминание 301 с `/web` (их нет). Проверка: после перегенерации сертификата `https://athletica.crm/` показывает лендинг, «Войти» ведёт на `https://app.athletica.crm/login`, вход работает
- [x] 4.2 `README.md`: строка `/etc/hosts` с `app.athletica.crm`, адрес CRM в dev — `https://app.athletica.crm/`. Проверка: шаги README воспроизводятся с нуля

## 5. Итоговая проверка

- [x] 5.1 `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format && npm run check`; `openspec validate public-landing-app-subdomain --strict`. Проверка: всё зелёное

## Workflow follow-up

- Миграция прода (DNS, сертификат, `.env`, адрес уведомлений в кабинете ЮKassa) — по `DEPLOY.md` после мержа, вручную.
- Содержание и структура лендинга — отдельное изменение.
- Архивировать изменение после выката и проверки на проде.
