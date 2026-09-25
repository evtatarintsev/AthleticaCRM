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
| `src/account/`       | профиль, смена пароля, смена филиала                                                                                |
| `src/settings/`      | страница настроек и справочники: `directory/` — общая страница справочника, остальное — тарифы, роли, доп. поля     |
| `src/ui/`            | общие элементы страниц: заголовок, аватар, диалог подтверждения, панель выбранных записей                           |

Базовый путь (`/web`) — константа сборки `__BASE_PATH__` из `vite.config.ts`, в коде — `BASE_PATH` из `src/config.ts`.

### Раздел в KMP-клиенте или здесь

Навигация берёт разделы из `src/app/sections.ts`. Пока раздел не перенесён целиком, пункт — обычная
`<a href="/<раздел>">` в KMP-клиент. Перенесли раздел — `migratedSections.<раздел> = true`; тест
проверяет, что у перенесённого раздела есть маршрут.

### Типовые приёмы

`any`, `as` (кроме `as const`), `!` и `@ts-*`-комментарии запрещены линтером, `eslint-disable` в коде
не действует. Вместо них — приёмы ниже.

**Контракты API.** После изменения схем в `shared` или маршрутов на сервере:

```bash
npm run contracts   # :server:generateWebContracts, результат — src/api/generated/contracts.ts
```

Файл коммитится; CI-job `contracts` падает, если он устарел. Руками схемы API не пишутся.

**Декодирование вместо приведения.** Данные извне — `unknown`, тип получается разбором схемой:

```ts
// Плохо: const settings = JSON.parse(raw) as Settings;
const SettingsSchema = z.object({ locale: LocaleSchema });
const parsed = SettingsSchema.safeParse(JSON.parse(raw));
const settings = parsed.success ? parsed.data : defaultSettings;
```

Ответы API декодирует `api.call` сгенерированной схемой эндпоинта: результат уже типизирован,
несовпадение — ошибка `contract`.

**Сужение вместо `!`.** Значение, которого может не быть, проверяется явно:

```ts
// Плохо: createRoot(document.getElementById("root")!)
const root = document.getElementById("root");
if (root === null) {
  throw new Error("Нет элемента #root");
}
createRoot(root);

// Плохо: items[0]!.name   (noUncheckedIndexedAccess)
const first = items[0];
const name = first?.name ?? "";

// type guard для unknown
function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null;
}
```

**Исчерпывающий `switch`.** Объединения разбираются без `default`: новый вариант (например, в
сгенерированном контракте) ломает компиляцию во всех местах разбора.

```ts
function errorText(error: ApiError, t: Translate): string {
  switch (error.kind) {
    case "business":
      return error.message;
    case "unauthenticated":
      return t("error.sessionExpired");
    case "unavailable":
      return t("error.serviceUnavailable");
    case "contract":
      return t("error.contract");
  }
}
```

Для таблиц соответствия вместо `switch` — `Record<Union, T>`: пропущенный ключ — ошибка компиляции
(см. `lib/currency.ts`).

**Необязательные пропсы.** С `exactOptionalPropertyTypes` в `prop?: T` нельзя передать `undefined`.
Либо объявить `prop?: T | undefined`, либо не передавать проп:

```tsx
<SectionLink {...(onNavigate === undefined ? {} : { onNavigate })} />
```

**Моки в тестах** — типизированные фабрики, а не `as`:

```ts
const api = { call: vi.fn<ApiClient["call"]>() } satisfies ApiClient;
```

### Исключения из строгих правил

Исключения задаются только в конфигурации, с причиной:

- `type-coverage` не проверяет `src/app/router.tsx` и `src/app/router.types.test.ts`. Тип роутера
  TanStack Router содержит `any` в объявлениях библиотеки (родитель корневого маршрута), и `--strict`
  засчитывает его каждому идентификатору с этим типом. Поэтому тип роутера не выходит за `router.tsx`:
  наружу отдаются `createAppRouting`, `StaticAppPath` и `isStaticAppPath`. ESLint (`no-unsafe-*`)
  проверяет эти файлы как обычно.
- ESLint `only-throw-error` разрешает выбрасывать `Redirect` из `@tanstack/router-core`: так роутер
  прерывает `beforeLoad`.
- Фильтрация TanStack Table не используется: у `FilterFn` значение фильтра объявлено как `any`,
  и `type-coverage --strict` засчитывает его каждому месту, где функция фильтрации упомянута.
  Списки фильтруются до передачи в таблицу (`useMemo` над данными), таблица отвечает за выбор строк.
- Radix подключается пакетами `@radix-ui/react-*`, а не общим `radix-ui`: объявления
  `@radix-ui/react-select` не проходят `skipLibCheck: false` с `exactOptionalPropertyTypes`.
  Выпадающий список — нативный `<select>` (`components/ui/native-select.tsx`).

## Деплой

`Dockerfile` собирает статику и кладёт её в nginx под `/web/`. В проде это сервис
`frontend` в `docker-compose.prod.yaml`; внешний nginx проксирует на него `/web/`.
Образ `ghcr.io/<repo>/frontend` собирает CI на push в `master`;
для стенда — `./deploy_to_stand.sh <ip> --only frontend`.
