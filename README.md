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

### 🚀 Локальный запуск через https://athletica.crm

Dev-nginx (`docker-compose.dev.yaml`) проксирует фронт (Vite, :5173), API (Ktor, :8080) и RustFS
по HTTPS на самоподписанном сертификате. HTTPS нужен, чтобы страница была secure context
(без него в браузере нет `crypto.randomUUID`, `navigator.clipboard` и т.п.).

1. Хосты в `/etc/hosts`:
   ```
   127.0.0.1 athletica.crm minio.athletica.crm console.minio.athletica.crm
   ```
2. Сертификат (один раз; кладётся в `nginx/certs/`, в git не попадает):
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
   ./gradlew server:run
   ```
6. Фронтенд:
   ```bash
   cd web && npm ci && npm run dev
   ```
7. Открыть https://athletica.crm/.

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