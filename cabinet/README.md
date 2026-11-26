# Кабинет клиента

Веб-кабинет клиента на `clients.<домен>`: TypeScript + React + Vite. Отдельный проект со своим
образом `cabinet`; к CRM (`web/`) не относится и выпускается отдельно
(изменение `openspec/changes/client-cabinet-web`, design §2).

## Команды

```bash
npm run dev        # dev-сервер на http://localhost:5174, /api → локальный Ktor (API_PROXY)
npm run format
npm run check      # typecheck + eslint + type-coverage + prettier --check + vitest
npm run contracts  # перегенерировать src/api/generated/ из маршрутов /api/client-app/*
```

Локальный хост `clients.athletica.crm` разрешён в `vite.config.ts` (как `app.athletica.crm` у CRM).

## Правила

Те же, что у `web/` (см. `web/README.md` и раздел «Web Client» в `CLAUDE.md`): без `any`, `as`,
non-null `!` и `@ts-*`-директив, `eslint-disable` не действует, внешние данные декодируются
zod-схемой, видимые строки — из `src/i18n/ru.ts` / `en.ts`.

Код взят из `web/` копированием и дальше живёт своей жизнью: общего npm-пакета нет (design §2).

## Исключения из строгих правил

Нет.
