# web — веб-фронтенд на React

Новый веб-клиент AthleticaCRM: TypeScript + React + Vite + Tailwind CSS.
Живёт под префиксом `/web/` рядом со старым KMP-клиентом (`/`) и постепенно его заменяет.
Здесь каркас (сессия, раскладка, навигация, локализация RU/EN), вход и регистрация. Разделы
переносятся по одному; неперенесённые открываются в KMP-клиенте по общей HttpOnly-cookie.

## Команды

```bash
npm ci            # зависимости
npm run dev       # dev-сервер на :5173, /api → Ktor :8080 (или API_PROXY=http://…)
npm run check     # typecheck + eslint + prettier --check + тесты
npm run format    # prettier --write
npm run build     # сборка в dist/
```

Открыть: http://localhost:5173/web/ или, через dev-nginx (`docker-compose.dev.yaml`),
http://athletica.crm/web/.

## Устройство

| Каталог              | Что внутри                                                                                                          |
| -------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `src/api/`           | `generated/contracts.ts` — контракты из сервера (`npm run contracts`), `client.ts` — клиент, возвращает `ApiResult` |
| `src/app/`           | маршруты (TanStack Router), раскладка, навигация, реестр перенесённых разделов `sections.ts`                        |
| `src/auth/`          | экраны входа и регистрации, сценарий входа с выбором филиала, адрес возврата после входа                            |
| `src/query/`         | TanStack Query: `apiQuery` (филиал в ключе), `ApiResult` → исключение `ApiFailure` для запросов                     |
| `src/forms/`         | обёртки полей TanStack Form с `autocomplete`, подписью и доступной ошибкой                                          |
| `src/i18n/`          | словари `ru` (источник ключей) и `en: Messages<typeof ru>`, перевод, форматирование дат, чисел и денег              |
| `src/components/ui/` | компоненты shadcn/ui, приведённые к строгим правилам                                                                |

Базовый путь (`/web`) — константа сборки `__BASE_PATH__` из `vite.config.ts`, в коде — `BASE_PATH` из `src/config.ts`.

### Раздел в KMP-клиенте или здесь

Навигация берёт разделы из `src/app/sections.ts`. Пока раздел не перенесён целиком, пункт — обычная
`<a href="/<раздел>">` в KMP-клиент. Перенесли раздел — `migratedSections.<раздел> = true`; тест
проверяет, что у перенесённого раздела есть маршрут.

### Исключения из строгих правил

Исключения задаются только в конфигурации, с причиной:

- `type-coverage` не проверяет `src/app/router.tsx` и `src/app/router.types.test.ts`. Тип роутера
  TanStack Router содержит `any` в объявлениях библиотеки (родитель корневого маршрута), и `--strict`
  засчитывает его каждому идентификатору с этим типом. Поэтому тип роутера не выходит за `router.tsx`:
  наружу отдаются `createAppRouting`, `StaticAppPath` и `isStaticAppPath`. ESLint (`no-unsafe-*`)
  проверяет эти файлы как обычно.
- ESLint `only-throw-error` разрешает выбрасывать `Redirect` из `@tanstack/router-core`: так роутер
  прерывает `beforeLoad`.
- Radix подключается пакетами `@radix-ui/react-*`, а не общим `radix-ui`: объявления
  `@radix-ui/react-select` не проходят `skipLibCheck: false` с `exactOptionalPropertyTypes`.
  Выпадающий список — нативный `<select>` (`components/ui/native-select.tsx`).

## Деплой

`Dockerfile` собирает статику и кладёт её в nginx под `/web/`. В проде это сервис
`frontend` в `docker-compose.prod.yaml`; внешний nginx проксирует на него `/web/`.
Образ `ghcr.io/<repo>/frontend` собирает CI на push в `master`;
для стенда — `./deploy_to_stand.sh <ip> --only frontend`.
