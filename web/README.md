# web — веб-фронтенд на React

Веб-клиент AthleticaCRM: TypeScript + React + Vite + Tailwind CSS. Отдаётся с корня (`/`);
KMP-клиент, который он заменил, удалён (см. `openspec/changes/rewrite-web-frontend-typescript`).
Старые адреса с префиксом `/web/...` постоянно перенаправляются на путь без префикса на уровне nginx.

## Команды

```bash
npm ci            # зависимости
npm run dev       # dev-сервер на :5173, /api → Ktor :8080 (или API_PROXY=http://…)
npm run check     # typecheck + eslint + prettier --check + тесты
npm run format    # prettier --write
npm run build     # сборка в dist/
```

Открыть: http://localhost:5173/ или, через dev-nginx (`docker-compose.dev.yaml`), https://app.athletica.crm/ (лендинг — https://athletica.crm/).
Dev-nginx работает по HTTPS на самоподписанном сертификате — настройка в корневом
[README](../README.md#-локальный-запуск-через-httpsappathleticacrm).

## Устройство

| Каталог              | Что внутри                                                                                                          |
| -------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `src/api/`           | `generated/contracts.ts` — контракты из сервера (`npm run contracts`), `client.ts` — клиент, возвращает `ApiResult` |
| `src/app/`           | маршруты (TanStack Router), раскладка, навигация, разделы `sections.ts`                                             |
| `src/auth/`          | экраны входа и регистрации, сценарий входа с выбором филиала, адрес возврата после входа                            |
| `src/query/`         | TanStack Query: `apiQuery` (филиал в ключе), `ApiResult` → исключение `ApiFailure` для запросов                     |
| `src/forms/`         | обёртки полей TanStack Form с `autocomplete`, подписью и доступной ошибкой                                          |
| `src/i18n/`          | словари `ru` (источник ключей) и `en: Messages<typeof ru>`, перевод, форматирование дат, чисел и денег              |
| `src/components/ui/` | компоненты shadcn/ui, приведённые к строгим правилам                                                                |
| `src/account/`       | профиль, смена пароля, смена филиала                                                                                |
| `src/settings/`      | страница настроек и справочники: `directory/` — общая страница справочника, остальное — тарифы, роли, доп. поля     |
| `src/ui/`            | общие элементы страниц: заголовок, аватар, диалог подтверждения, панель выбранных записей                           |
| `src/reports/`       | раздел «Отчёты»: реестр отчётов, фильтры, шаблоны карточки и экрана, таблица показателей, `charts/` — графики       |

Базовый путь клиента — константа сборки `__BASE_PATH__` из `vite.config.ts` (сейчас `""`), в коде —
`BASE_PATH` из `src/config.ts`; используется только для склейки адреса возврата после входа.

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

### Раздел «Отчёты»: как подключается отчёт

Каркас раздела — изменение `reports-page`; каждый отчёт (`reports-revenue`, `reports-debts`, …)
встраивается в него, почти не трогая общие файлы:

- **Реестр.** В `src/reports/registry.ts` у записи отчёта появляется `impl: { Card, Screen }`.
  `Card` загружает свою сводку отдельным `useQuery` и рисует `ReportCard` (состояния «загрузка»,
  «не удалось загрузить» с повтором, «готово»); `Screen` рисует `ReportScreen` с `ReportTile`,
  `ReportSection`, `ReportPeople` и строкой «Как считается». Без `impl` — заглушка «скоро», а
  `/reports/<id>` отвечает «не найдено». Маршрут один на все отчёты, `router.tsx` не правится.
- **Права.** `requires` записи — права сверх `CAN_VIEW_REPORTS`; сервер проверяет те же права в
  маршруте отчёта.
- **Таблица показателей.** На сервере — источник `ReportTableSource` (`read/reports/`) поверх
  проекции-сводки отчёта, с `requires` теми же правами; регистрируется в
  `ReadViews.reportTableSources`. Строки попадают и в таблицу, и в выгрузку Excel/CSV.
- **Словари.** Ключи только своего префикса `reports.<id>.*` (`name` и `question` уже есть).
- **Графики** — только из `src/reports/charts` (`BarsChart`, `Sparkline`, `CompareBars`,
  `DonutChart`, `ShareBars`): отчёты не импортируют Recharts напрямую, передают числа и готовые
  подписи, цвет — группа отчёта. Раздел грузится отдельным чанком (`lazyRouteComponent`), поэтому
  Recharts не попадает в основной бандл.
- **Период** считает сервер (`ReportPeriods` в `shared`): отчёт принимает `ReportFilter` и
  отдаёт `period` и `basePeriod`; «сегодня» берёт маршрут в часовом поясе организации.

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
- Типы `@reduxjs/toolkit` подменены для `tsc` (`paths` в `tsconfig.base.json` →
  `src/vendor-types/reduxjs-toolkit.d.ts`): пакет — зависимость Recharts, его `index.d.mts` не
  проходит `exactOptionalPropertyTypes`. Код `web/` toolkit не импортирует; подмена описывает только
  типы внутреннего хранилища, которые видны в объявлениях Recharts, и на сборку не влияет — Vite
  берёт настоящий пакет. Новый тип toolkit в объявлениях Recharts уронит `tsc` — дописать в подмену.

## Деплой

`Dockerfile` собирает статику и кладёт её в nginx под корнем. В проде это сервис `frontend`
в `docker-compose.prod.yaml`; внешний nginx (`nginx/prod.conf.template`) проксирует на него `/`
хоста CRM `app.<домен>`.

Тот же образ отдаёт публичный лендинг — `landing/index.html`, статичный HTML без React и сборки.
Внутренний nginx (`nginx.conf`) обслуживает его на порту `8081`, внешний проксирует туда корень
основного домена и перенаправляет `/login` и `/sign-up` на `app.<домен>`, поэтому ссылки в лендинге
относительные. Строки лендинга написаны прямо в HTML, а не берутся из `src/i18n/`: словари — часть
React-приложения, а у лендинга пока один язык.
Образ `ghcr.io/<repo>/frontend` собирает CI на push в `master`;
для стенда — `./deploy_to_stand.sh <ip> --only frontend`.
