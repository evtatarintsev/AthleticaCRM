# Athletica CRM
## Система управления спортивными школами

### 📚 Документация

**Начните с**: [`docs/INDEX.md`](./docs/INDEX.md) — навигация по всей документации

**Основные документы**:
- 📋 [`docs/USECASES_CORE.md`](./docs/USECASES_CORE.md) — 28+ юзкейсов ядра CRM (4 модуля)
- 🏗️ [`docs/ARCHITECTURE_CORE.md`](./docs/ARCHITECTURE_CORE.md) — архитектура, модель данных, бизнес-правила
- 📅 [`docs/PLANNING_SUMMARY.md`](./docs/PLANNING_SUMMARY.md) — MVP план, timeline, критичные решения
- ⚡ [`docs/QUICK_REFERENCE.md`](./docs/QUICK_REFERENCE.md) — быстрая справка для разработчиков

**Другие документы**:
- [`docs/client-balance.md`](./docs/client-balance.md) — учет баланса клиентов
- [`docs/employee-access-flow.md`](./docs/employee-access-flow.md) — доступы сотрудников

---

### 🚀 Локальный запуск через https://app.athletica.crm

Dev-nginx (`docker-compose.dev.yaml`) отдаёт лендинг на `athletica.crm` и проксирует CRM на
`app.athletica.crm` — фронт (Vite, :5173) и API (Ktor, :8080), — а также RustFS по HTTPS на самоподписанном сертификате. HTTPS нужен, чтобы страница была secure context
(без него в браузере нет `crypto.randomUUID`, `navigator.clipboard` и т.п.).

0. Java и Node ставит [mise](https://mise.jdx.dev): `mise trust && mise install`
   (список задач — `mise tasks`).
1. Хосты в `/etc/hosts`:
   ```
   127.0.0.1 athletica.crm app.athletica.crm minio.athletica.crm console.minio.athletica.crm
   ```
2. Сертификат (кладётся в `nginx/certs/`, в git не попадает; перевыпустить, если в нём нет
   `app.athletica.crm`):
   ```bash
   ./nginx/gen-dev-cert.sh
   ```
3. Добавить сертификат в доверенные (macOS) — без этого браузер покажет предупреждение,
   а Ktor не сможет подписывать ссылки на файлы через `https://minio.athletica.crm`:
   ```bash
   sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain nginx/certs/dev.crt
   ```
   После этого перезапустить браузер.
4. Инфраструктура (переменные `POSTGRES_*`, `MINIO_*` — из `.envrc`):
   ```bash
   docker compose -f docker-compose.dev.yaml up -d
   ```
   После изменения `docker-compose.dev.yaml` контейнеры нужно пересоздать (`up -d`),
   `docker compose restart` новых томов и портов не подхватывает.
5. Бэкенд с `MINIO_PUBLIC_ENDPOINT=https://minio.athletica.crm` (`MINIO_ENDPOINT` остаётся
   `http://localhost:9000`):
   ```bash
   mise run server
   ```
6. Фронтенд:
   ```bash
   cd web && npm ci && cd .. && mise run web
   ```
7. Открыть https://app.athletica.crm/ (CRM) или https://athletica.crm/ (лендинг).

---

### Единый язык (Ubiquitous language)

- **AthleticaCRM** — название проекта
- **Организация (Школа)** — спортивная организация, предоставляющая услуги
- **Сотрудник** — работник, занимающий определенную должность
- **Клиент** — участник занятий
- **Группа** — класс/курс (e.g. "Йога ПН/СР")
- **Дисциплина** — вид спорта/активности
- **Абонемент (Membership)** — подписка клиента на занятия
- **Занятие (Instance)** — реальное занятие (e.g. "Йога в ПН 18:00")
- **Посещение (Attendance)** — факт посещения клиентом занятия
- **Тариф (Tariff)** — тарифный план (цена, период, кол-во занятий)
- **Расписание (Schedule)** — расписание группы на неделю
- **Помещение (Room)** — зал, где проходят занятия