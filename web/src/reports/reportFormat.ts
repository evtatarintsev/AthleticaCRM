import type { DateRange, ReportBetter, ReportValue } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Оттенок плашки изменения: к лучшему, к худшему или без изменения. */
export type ChangeTone = "good" | "bad" | "neutral";

/** Изменение показателя к прошлому периоду, готовое к показу. */
export interface ReportChangeView {
  /** Текст плашки: «▲ 12,4%», «▼ 3 п.п.». */
  readonly text: string;
  /** Оттенок плашки. */
  readonly tone: ChangeTone;
}

/**
 * Изменение [value] к [base] — то же правило, что `reportChange` на сервере: для сумм и количеств —
 * относительное к модулю прошлого значения, для долей — разница в долях; `null` — сравнивать не с чем.
 */
export function reportChange(value: ReportValue, base: ReportValue | null): number | null {
  if (base === null) {
    return null;
  }
  switch (value.type) {
    case "amount":
      return base.type === "amount" && base.amount.minorUnits !== 0
        ? (value.amount.minorUnits - base.amount.minorUnits) / Math.abs(base.amount.minorUnits)
        : null;
    case "count":
      return base.type === "count" && base.count !== 0
        ? (value.count - base.count) / Math.abs(base.count)
        : null;
    case "percent":
      return base.type === "percent" ? value.share - base.share : null;
  }
}

/** Доля [share] в процентах с одним знаком после запятой: `0.1234` → «12,3». */
function percentNumber({ format }: I18n, share: number): string {
  return format.number(Math.round(Math.abs(share) * 1000) / 10);
}

/** Значение показателя [value] строкой: сумма в валюте, число или процент. */
export function formatReportValue(i18n: I18n, value: ReportValue): string {
  switch (value.type) {
    case "amount":
      return i18n.format.money(value.amount);
    case "count":
      return i18n.format.number(value.count);
    case "percent":
      return `${percentNumber(i18n, value.share)}%`;
  }
}

/**
 * Плашка изменения [value] к [base]: стрелка и величина, цвет — по [better]
 * (для долгов рост — к худшему). Без прошлого значения — `null`, плашка не показывается.
 */
export function reportChangeView(
  i18n: I18n,
  value: ReportValue,
  base: ReportValue | null,
  better: ReportBetter,
): ReportChangeView | null {
  const change = reportChange(value, base);
  if (change === null) {
    return null;
  }
  const amount = percentNumber(i18n, change);
  const magnitude =
    value.type === "percent" ? i18n.t("reports.change.points", { value: amount }) : `${amount}%`;
  if (amount === i18n.format.number(0)) {
    return { text: magnitude, tone: "neutral" };
  }
  const up = change > 0;
  return {
    text: `${up ? "▲" : "▼"} ${magnitude}`,
    tone: up === (better === "up") ? "good" : "bad",
  };
}

/** Даты периода [range] строкой: «1 ноября – 27 ноября». */
export function formatRange(i18n: I18n, range: DateRange): string {
  return i18n.t("reports.range", {
    from: i18n.format.dayMonth(range.from),
    to: i18n.format.dayMonth(range.to),
  });
}
