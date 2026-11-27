# Tasks

Одно изменение — один PR. Группа 1 идёт первой: от неё зависит выбор библиотеки (design §1).
Задачи 3.1–3.2 (общая выгрузка) от отчётов не зависят и могут уйти отдельным PR раньше.
Изменения отчётов (`reports-revenue` и остальные) начинаются после слияния этого PR.

## 1. Библиотека графиков

- [ ] 1.1 Добавить `recharts` в `web/package.json`; `BarsChart` в `web/src/reports/charts/` по design §1 (данные, цвет столбца функцией, подсказка готовой строкой, `aria-label`, необязательные `width`/`height`); vitest на `aria-label` и подсказку; `npm run check` проходит без `as`, `!` и исключений в конфигурации. Если не проходит — остановиться и пересмотреть design §1
- [ ] 1.2 `Sparkline`, `CompareBars`, `DonutChart` (легенда списком, число в центре) и `ShareBars` (без библиотеки); цвета групп — CSS-переменные темы; vitest на подписи и легенду

## 2. Право и навигация

- [ ] 2.1 `CAN_VIEW_REPORTS` в `Permission.kt` с KDoc; подписи `permission.CAN_VIEW_REPORTS.*` в `ru.ts` / `en.ts`; место в окне роли рядом с `CAN_VIEW_FINANCE`; право у роли управляющего в `DemoData`; `cd web && npm run contracts`; `./gradlew build`
- [ ] 2.2 Раздел `reports` в `sections.ts` (`ChartColumnIcon`, между «Финансами» и «Настройками»), проп `canViewReports` в `Navigation`/`AppLayout` по образцу `canViewFinance`; тест «без права пункта нет», дополнить `router.test.tsx`

## 3. Сервер: выгрузка, период, таблица показателей

- [ ] 3.1 `routes/export/TableExport.kt` по design §2: `ExportFormat` (в `shared`), `ExportCell`, `ExportTable`, `respondExport`; `fastexcel` writer — в `implementation`; тесты: BOM, кавычки, апостроф перед `=` в тексте, `-80` в числовой ячейке без апострофа, в xlsx суммы и проценты — числовые ячейки с форматом (чтение через `fastexcel-reader`)
- [ ] 3.2 Перевести выгрузки журнала и ДДС (`FinanceRoutes`) и клиентов (`ClientsRoutes`) на `respondExport`, удалить `financeCsv` и `generateCsvContent`; клиенты в xlsx — настоящая книга (#342); тесты маршрутов на оба формата; изменения формата файлов — в описании PR
- [ ] 3.3 `ReportFilter` и `ReportPeriods` в `shared/.../reports/` по design §3 (пункты, свой период smart constructor-ом, оба режима сравнения, `today` извне); тесты на каждый пункт таблицы, конец месяца (31 марта → 28/29 февраля), переход через год и квартал
- [ ] 3.4 `ReportTableSource` в `read/reports/`, пустой список в `ReadViews`; маршруты `/api/reports/table` и `/api/reports/table/export` (design §4): `CAN_VIEW_REPORTS`, источники по правам, «сегодня» — из routes в часовом поясе организации; тест маршрутов: без права — `PERMISSION_DENIED`, источник с `CAN_VIEW_FINANCE` не попадает в ответ и в файл без права; `cd web && npm run contracts`

## 4. Страница раздела

- [ ] 4.1 Реестр `web/src/reports/registry.ts` (девять записей, группы, иконки, `requires` по design §4) и правило видимости `visibleReports(permissions)`; vitest: без `CAN_VIEW_FINANCE` нет «Выручки», «Доходов и расходов», «Филиалов»; без `CAN_VIEW_CLIENT_BALANCE` нет «Долгов клиентов»; группа без карточек скрыта
- [ ] 4.2 Схема search-параметров (design §3) на zod с запасным значением «Этот месяц»; фильтры периода (свой период — два поля даты и выбор «те же дни перед ним» / «тот же отрезок месяцем раньше») и филиала (доступные сотруднику, «Все филиалы»); vitest: неверные параметры → значения по умолчанию, `from` > `to` не применяется
- [ ] 4.3 Маршруты `/reports` и `/reports/$reportId` через `lazyRouteComponent` (design §6); без `CAN_VIEW_REPORTS` — экран «нет доступа», неизвестный отчёт или заглушка — «не найдено»; `vite build` — `recharts` только в чанке раздела
- [ ] 4.4 `ReportStubCard` и страница с группами «Деньги», «Клиенты», «Команда», «Сравнения» по макету; переключатель «Карточки / Таблица» (`view` в search-параметрах); словари `reports.*`; vitest на состав страницы с разными правами

## 5. Шаблоны и таблица

- [ ] 5.1 `ReportCard` (главная цифра, изменение с `better`, `Sparkline`, предупреждение, ссылка с сохранением фильтров, состояния «загрузка» и «не удалось загрузить» с повтором); vitest: рост при `better: "down"` красный, без прошлого периода изменения нет
- [ ] 5.2 `ReportScreen`, `ReportTile`, `ReportSection`, `ReportPeople`, строка «Как считается»; «← Все отчёты» сохраняет фильтры; vitest на шаблон с тестовыми данными
- [ ] 5.3 `ReportsTable` на `/api/reports/table` (колонки по design §5, пустое состояние, кнопки «Excel» / «CSV» вызывают `/api/reports/table/export` и отдают ответ в `downloadBlob`, без строк недоступны); vitest: запрос выгрузки с текущим фильтром и форматом
- [ ] 5.4 Описать в `web/README.md`, как изменение отчёта подключается к каркасу: запись `impl` в реестре, источник `ReportTableSource` в `ReadViews`, ключи `reports.<id>.*`, графики только из `reports/charts`

## 6. Проверка

- [ ] 6.1 Проверить в браузере на 1280 px и 360 px: владелец видит все девять заглушек, сотрудник с одним `CAN_VIEW_REPORTS` — пять, без права — пункта нет и `/reports` отвечает «нет доступа»
- [ ] 6.2 Выгрузить клиентов и журнал финансов в CSV и xlsx, открыть в Excel или LibreOffice: кириллица, колонки, суммы числами; `mise run e2e`, если e2e касается выгрузок
- [ ] 6.3 `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check`
