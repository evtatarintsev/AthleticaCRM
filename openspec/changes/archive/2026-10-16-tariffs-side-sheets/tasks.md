## 1. Строка тарифа и панель тарифа

- [x] 1.1 Создать `web/src/settings/tariffs/TariffRow.tsx`: строка тарифа без поведения (название, условия, стоимость через `format.money`, пометка «В архиве»), и `tariffTerms.ts` с `tariffTermsText` (занятия или «Безлимит» + срок, из `TariffCard`/`durationText`; отдельный файл — правило `react-refresh/only-export-components`). Проверка: `npm run check` в `web/` проходит
- [x] 1.2 Создать `web/src/settings/tariffs/TariffSheet.tsx`: `EditSheet` + `EditSheetForm`, поля, схема и разбор из `TariffDialog` без изменений, `dirty`/`submitting` из состояния формы, `useEditSheet().close()` после успешного `onSave`, ошибка сервера — `FormAlert`; в режиме изменения — блок «Архив» с «В архив»/«Восстановить» через `onArchiveToggle`, панель остаётся открытой. Перенести `TariffTerms`, удалить `TariffDialog.tsx`. Проверка: `npm run check`

## 2. Панель списка тарифов в настройках

- [x] 2.1 Создать `web/src/settings/tariffs/TariffsSheet.tsx` по образцу `RolesSheet`: `EditSheet size="lg"`, кнопка «Новый тариф» (недоступна без валюты из `org/settings`), строки активных тарифов кнопками, свёрнутый блок «Архив (N)» (не показывается без архивных), заглушка загрузки, ошибка, пустое состояние; вложенный `TariffSheet`, в `editing` — id тарифа, `initial` из данных списка; создание/изменение/архивация через `tariffs/create|update|archive`, инвалидация `tariffs/list`, тосты архивации. Удалить `TariffsPage.tsx`. Проверка: `npm run check`
- [x] 2.2 Добавить `"tariffs"` в `SettingsPanelSchema`, пункт «Тарифы абонементов» в `SettingsPage.tsx` перевести на `panel("tariffs")`, зарегистрировать `SHEETS.tariffs`. Проверка: пункт открывает панель поверх `/settings`, адрес `/settings?panel=tariffs`
- [x] 2.3 Удалить `tariffsRoute` и `"/settings/tariffs"` из `staticAppPaths` в `web/src/app/router.tsx`; поправить `SettingsPage.test.tsx` и тесты роутера, если они ссылаются на адрес. Проверка: `npm run check`

## 3. Тесты

- [x] 3.1 Переписать `TariffsPage.test.tsx` в `TariffsSheet.test.tsx` на `/settings?panel=tariffs`: строка с условиями и стоимостью, пустое состояние, архив свёрнут и раскрывается, блока архива нет без архивных, создание и изменение тарифа, безлимит. Проверка: `npx vitest run src/settings/tariffs`
- [x] 3.2 Тесты `TariffSheet`: неверные значения показывают ошибки без запроса; изменение поля и закрытие спрашивают подтверждение; ошибка сервера оставляет панель открытой; «В архив» оставляет панель открытой с «Восстановить» и не сбрасывает изменённое название; в создании нет действий архива. Проверка: `npx vitest run src/settings/tariffs`

## 4. Завершение

- [x] 4.1 Обновить словари `ru.ts`/`en.ts`: подпись блока «Архив ({count})», подписи действий в панели; убрать ключи, которые после переноса не используются (`tariffs.archiveTariff`, `tariffs.restoreTariff`, `tariffs.editTariff` — если не остались в aria-label). В `web/` выполнить `npm run format` и `npm run check` — оба проходят
- [x] 4.2 Вручную в превью: настройки → «Тарифы абонементов» открывает панель, создание и изменение во вложенной панели, Esc с изменёнными условиями спрашивает подтверждение, «В архив» и «Восстановить» из панели переносят тариф между группами, панель остаётся открытой; на мобильной ширине панель и строки читаемы
