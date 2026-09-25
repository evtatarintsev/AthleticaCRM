# web — веб-фронтенд на React

Новый веб-клиент AthleticaCRM: TypeScript + React + Vite + Tailwind CSS.
Живёт под префиксом `/web/` рядом со старым KMP-клиентом (`/`) и постепенно его заменяет.
Сейчас здесь только вход и регистрация; после входа пользователь уходит на `/`,
где KMP-клиент подхватывает сессию из HttpOnly-cookie.

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

| Каталог       | Что внутри                                                                                                           |
| ------------- | -------------------------------------------------------------------------------------------------------------------- |
| `src/api/`    | `schemas.ts` — зеркало Kotlin-схем из `shared`, `client.ts` — fetch-клиент, возвращает `ApiResult` вместо исключений |
| `src/auth/`   | экраны входа и регистрации, сценарий входа с выбором филиала                                                         |
| `src/ui/`     | общие компоненты: каркас, поля, кнопки                                                                               |
| `src/i18n.ts` | строки интерфейса                                                                                                    |

Контракты пока дублируются вручную (`src/api/schemas.ts`); следующий шаг — генерация из `shared`.

## Деплой

`Dockerfile` собирает статику и кладёт её в nginx под `/web/`. В проде это сервис
`frontend` в `docker-compose.prod.yaml`; внешний nginx проксирует на него `/web/`.
Образ `ghcr.io/<repo>/frontend` собирает CI на push в `master`;
для стенда — `./deploy_to_stand.sh <ip> --only frontend`.
