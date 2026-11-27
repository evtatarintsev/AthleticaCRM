/** Пункт легенды графика. */
export interface LegendItem {
  /** Уникальный ключ пункта. */
  readonly key: string;
  /** Цвет метки. */
  readonly color: string;
  /** Подпись. */
  readonly label: string;
  /** Необязательное значение справа (готовая строка). */
  readonly value?: string;
}

/** Легенда графика списком: цветная метка, подпись и значение. */
export function ChartLegend({
  items,
  vertical = false,
}: {
  readonly items: readonly LegendItem[];
  readonly vertical?: boolean;
}) {
  return (
    <ul
      className={
        vertical
          ? "flex flex-col gap-2 text-sm"
          : "text-muted-foreground flex flex-wrap gap-x-4 gap-y-1 text-sm"
      }
    >
      {items.map((item) => (
        <li key={item.key} className="flex items-center gap-2">
          <span
            className="size-2.5 shrink-0 rounded-full"
            style={{ background: item.color }}
            aria-hidden="true"
          />
          <span className="min-w-0 flex-1">{item.label}</span>
          {item.value === undefined ? null : <span className="font-semibold">{item.value}</span>}
        </li>
      ))}
    </ul>
  );
}
