import {
  DashboardWidgetIdSchema,
  LocalDateSchema,
  type BirthdayWindow,
  type ClientListRequest,
  type DashboardSettings,
  type DashboardWidget,
  type DashboardWidgetBirthdays,
  type DashboardWidgetDebtors,
  type DashboardWidgetId,
  type DateRange,
  type LocalDate,
} from "@/api/generated/contracts";
import type { ClientListSearch } from "@/clients/clientListSearch";
import type { PlainMessageKey } from "@/i18n/context";

/** Значение количества элементов в виджете по умолчанию (паритет с KMP-клиентом). */
export const DEFAULT_WIDGET_LIMIT = 10;

/** Минимальное и максимальное значение лимита виджета. */
export const MIN_WIDGET_LIMIT = 1;
export const MAX_WIDGET_LIMIT = 50;

/** Типы виджетов главной в порядке, в котором `default()` создаёт их в KMP-клиенте. */
const WIDGET_TYPES = ["sessions", "debtors", "birthdays"] as const;

/** Новый идентификатор виджета. */
function newWidgetId(): DashboardWidgetId {
  return DashboardWidgetIdSchema.parse(crypto.randomUUID());
}

/** Виджет типа [type] с настройками по умолчанию и новым идентификатором. */
function defaultWidgetOf(type: DashboardWidget["type"]): DashboardWidget {
  switch (type) {
    case "sessions":
      return { type: "sessions", id: newWidgetId(), title: null };
    case "debtors":
      return { type: "debtors", id: newWidgetId(), title: null, limit: DEFAULT_WIDGET_LIMIT };
    case "birthdays":
      return {
        type: "birthdays",
        id: newWidgetId(),
        title: null,
        window: "TODAY",
        limit: DEFAULT_WIDGET_LIMIT,
      };
  }
}

/** Настройки дашборда по умолчанию: по одному виджету каждого типа, все видимы. */
export function defaultDashboardSettings(): DashboardSettings {
  const widgets = WIDGET_TYPES.map(defaultWidgetOf);
  return { widgets, layout: widgets.map((widget) => widget.id) };
}

/**
 * Настройки [settings] с добавленными недостающими типами виджетов (паритет с
 * `DashboardSettings.withDefaults()` KMP-клиента): пустой пул — полный набор по умолчанию,
 * иначе в конец пула и раскладки добавляется по одному экземпляру каждого отсутствующего типа.
 */
export function withDashboardDefaults(settings: DashboardSettings): DashboardSettings {
  if (settings.widgets.length === 0) {
    return defaultDashboardSettings();
  }
  const present = new Set(settings.widgets.map((widget) => widget.type));
  const missing = WIDGET_TYPES.filter((type) => !present.has(type)).map(defaultWidgetOf);
  if (missing.length === 0) {
    return settings;
  }
  return {
    widgets: [...settings.widgets, ...missing],
    layout: [...settings.layout, ...missing.map((widget) => widget.id)],
  };
}

/** Видимые виджеты [settings] в порядке раскладки. */
export function orderedVisible(settings: DashboardSettings): readonly DashboardWidget[] {
  const byId = new Map(settings.widgets.map((widget) => [widget.id, widget] as const));
  return settings.layout.flatMap((id) => {
    const widget = byId.get(id);
    return widget === undefined ? [] : [widget];
  });
}

/** Скрытые виджеты [settings]: есть в пуле, но не в раскладке. */
export function hiddenWidgets(settings: DashboardSettings): readonly DashboardWidget[] {
  const visible = new Set<DashboardWidgetId>(settings.layout);
  return settings.widgets.filter((widget) => !visible.has(widget.id));
}

/** Ключ локализованного заголовка по умолчанию для типа виджета [type]. */
export function defaultTitleKey(type: DashboardWidget["type"]): PlainMessageKey {
  switch (type) {
    case "sessions":
      return "home.todaySessionsTitle";
    case "debtors":
      return "home.debtorsTitle";
    case "birthdays":
      return "home.birthdaysTitle";
  }
}

/** Заголовок виджета [widget]: свой, если не пуст, иначе локализованный по умолчанию. */
export function resolveTitle(t: (key: PlainMessageKey) => string, widget: DashboardWidget): string {
  const title = widget.title;
  return title !== null && title.trim() !== "" ? title : t(defaultTitleKey(widget.type));
}

/** Заменяет в [settings] виджет с тем же идентификатором, что у [updated]. */
export function replaceWidget(
  settings: DashboardSettings,
  updated: DashboardWidget,
): DashboardSettings {
  return {
    ...settings,
    widgets: settings.widgets.map((widget) => (widget.id === updated.id ? updated : widget)),
  };
}

/** Скрывает виджет [id]: убирает из раскладки, пул не меняется — настройки виджета сохраняются. */
export function hideWidget(settings: DashboardSettings, id: DashboardWidgetId): DashboardSettings {
  return { ...settings, layout: settings.layout.filter((candidate) => candidate !== id) };
}

/** Показывает виджет [id]: добавляет в конец раскладки, если его там ещё нет. */
export function showWidget(settings: DashboardSettings, id: DashboardWidgetId): DashboardSettings {
  if (settings.layout.includes(id)) {
    return settings;
  }
  return { ...settings, layout: [...settings.layout, id] };
}

/** Переставляет видимый виджет с позиции [index] на [index + shift] в раскладке. */
export function moveVisibleWidget(
  settings: DashboardSettings,
  index: number,
  shift: -1 | 1,
): DashboardSettings {
  const target = index + shift;
  if (target < 0 || target >= settings.layout.length) {
    return settings;
  }
  const layout = [...settings.layout];
  const moved = layout.splice(index, 1)[0];
  if (moved === undefined) {
    return settings;
  }
  layout.splice(target, 0, moved);
  return { ...settings, layout };
}

/** Значение лимита, ограниченное диапазоном `[MIN_WIDGET_LIMIT, MAX_WIDGET_LIMIT]`. */
export function clampWidgetLimit(value: number): number {
  return Math.min(MAX_WIDGET_LIMIT, Math.max(MIN_WIDGET_LIMIT, value));
}

/** Запрос списка клиентов для виджета должников [widget]. */
export function debtorsRequest(widget: DashboardWidgetDebtors): ClientListRequest {
  return { hasDebt: true, limit: widget.limit };
}

/** Запрос списка клиентов для виджета дней рождения [widget] от дня [today]. */
export function birthdaysRequest(
  widget: DashboardWidgetBirthdays,
  today: LocalDate,
): ClientListRequest {
  return { birthday: birthdayRange(widget.window, today), limit: widget.limit };
}

/**
 * Диапазон дней рождения для окна [window] от дня [today] (буквальный порт
 * `BirthdayWindow.dateRange()` KMP-клиента, включая `WEEK` = `[today, today + 7]`).
 */
function birthdayRange(window: BirthdayWindow, today: LocalDate): DateRange {
  switch (window) {
    case "TODAY":
      return { from: today, to: today };
    case "TOMORROW": {
      const tomorrow = addDaysUtc(today, 1);
      return { from: tomorrow, to: tomorrow };
    }
    case "WEEK":
      return { from: today, to: addDaysUtc(today, 7) };
  }
}

/** Дата [date] плюс [days] дней, без сдвига по часовому поясу. */
function addDaysUtc(date: LocalDate, days: number): LocalDate {
  const [year = 1970, month = 1, day = 1] = date.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day) + days * 86_400_000);
  return LocalDateSchema.parse(shifted.toISOString().slice(0, 10));
}

/** Год даты [date] вида `2025-01-31`. */
export function yearOf(date: LocalDate): number {
  return Number(date.slice(0, 4));
}

/** Search-параметры страницы клиентов для «Показать всех» виджета должников. */
export function debtorsShowAllSearch(): ClientListSearch {
  return { debt: true };
}

/** Search-параметры страницы клиентов для «Показать всех» виджета дней рождения. */
export function birthdaysShowAllSearch(window: BirthdayWindow): ClientListSearch {
  switch (window) {
    case "TODAY":
      return { birthday: "today" };
    case "TOMORROW":
      return { birthday: "tomorrow" };
    case "WEEK":
      return { birthday: "week" };
  }
}
