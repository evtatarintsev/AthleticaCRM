/** Группа отчётов: задаёт цвет карточек и графиков. */
export type ReportGroup = "money" | "clients" | "team" | "compare";

/** Оттенок цвета группы: основной, средний (прошлый период, фон столбцов) и светлый фон. */
export type GroupTone = "main" | "mid" | "tint";

/** CSS-цвет группы [group] в оттенке [tone] — переменная темы из `index.css`. */
export function groupColor(group: ReportGroup, tone: GroupTone = "main"): string {
  switch (tone) {
    case "main":
      return `var(--color-report-${group})`;
    case "mid":
      return `var(--color-report-${group}-mid)`;
    case "tint":
      return `var(--color-report-${group}-tint)`;
  }
}

/** Цвет доли кольца группы [group] с номером [index]; после четвёртой палитра повторяется. */
export function seriesColor(group: ReportGroup, index: number): string {
  const palette = [
    groupColor(group, "main"),
    groupColor(group, "mid"),
    "var(--color-muted-foreground)",
    "var(--color-border)",
  ];
  return palette[index % palette.length] ?? groupColor(group);
}

/** Цвет столбца, который стоит отметить как тревожный (например, убыточный день). */
export const WARNING_COLOR = "#f59e0b";
