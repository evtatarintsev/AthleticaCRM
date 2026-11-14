import { describe, expect, it } from "vitest";
import { DashboardWidgetIdSchema, type DashboardSettings } from "@/api/generated/contracts";
import {
  defaultDashboardSettings,
  hideWidget,
  moveWidget,
  normalizeDashboardOrder,
  orderedVisible,
  showWidget,
} from "./dashboardSettings";

function fixture() {
  const [a, b, c, d] = defaultDashboardSettings().widgets;
  if (a === undefined || b?.type !== "debtors" || c === undefined || d === undefined)
    throw new Error("Missing defaults");
  const settings: DashboardSettings = {
    widgets: [a, { ...b, title: "Мой долг", limit: 7 }, c, d],
    layout: [d.id, a.id, c.id],
  };
  return { settings, a, b: settings.widgets[1] ?? b, c, d };
}

describe("общий порядок виджетов", () => {
  it("нормализует старые видимые слоты, сохраняя скрытые и все параметры", () => {
    const { settings, a, b, c, d } = fixture();
    const normalized = normalizeDashboardOrder(settings);
    expect(normalized.widgets).toEqual([d, b, a, c]);
    expect(normalized.layout).toEqual(settings.layout);
    expect(orderedVisible(normalized)).toEqual([d, a, c]);
    expect(normalizeDashboardOrder(normalized)).toEqual(normalized);
    expect(settings.widgets.map((widget) => widget.id)).toEqual([a.id, b.id, c.id, d.id]);
  });

  it("не меняет согласованный порядок и настройки со всеми выключенными виджетами", () => {
    const defaults = defaultDashboardSettings();
    expect(normalizeDashboardOrder(defaults)).toEqual(defaults);
    const hidden = { ...defaults, layout: [] };
    expect(normalizeDashboardOrder(hidden)).toEqual(hidden);
  });

  it("игнорирует неизвестные ссылки раскладки при нормализации", () => {
    const { settings } = fixture();
    const unknown = DashboardWidgetIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000999");
    const normalized = normalizeDashboardOrder({
      ...settings,
      layout: [unknown, ...settings.layout],
    });
    expect(normalized.widgets).toEqual(normalizeDashboardOrder(settings).widgets);
    expect(orderedVisible(normalized)).toEqual(orderedVisible(settings));
  });

  it("выключает и включает средний виджет без изменения общего порядка", () => {
    const { settings, b } = fixture();
    const normalized = normalizeDashboardOrder(settings);
    const shown = showWidget(normalized, b.id);
    expect(shown.layout).toEqual(normalized.widgets.map((widget) => widget.id));
    expect(hideWidget(shown, b.id)).toEqual(normalized);
    expect(showWidget(shown, b.id)).toBe(shown);
    expect(shown.widgets).toEqual(normalized.widgets);
  });

  it("перемещает выключенный экземпляр, сохраняя видимость и параметры", () => {
    const { settings, a, b, c, d } = fixture();
    const normalized = normalizeDashboardOrder(settings);
    const moved = moveWidget(normalized, 1, 2);
    expect(moved.widgets).toEqual([d, a, c, b]);
    expect(moved.layout).toEqual(normalized.layout);
    expect(showWidget(moved, b.id).layout).toEqual([d.id, a.id, c.id, b.id]);
    expect(moveWidget(normalized, 0, -1)).toBe(normalized);
    expect(moveWidget(normalized, 3, 1)).toBe(normalized);
    expect(moveWidget(normalized, -1, 1)).toBe(normalized);
    expect(moveWidget(normalized, 4, -1)).toBe(normalized);
  });
});
