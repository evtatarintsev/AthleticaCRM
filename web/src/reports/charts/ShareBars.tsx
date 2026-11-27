import { groupColor, type ReportGroup } from "./colors";

/** Полоса доли. */
export interface ShareItem {
  /** Уникальный ключ полосы. */
  readonly key: string;
  /** Подпись слева. */
  readonly label: string;
  /** Доля от 0 до 1: ширина полосы. */
  readonly share: number;
  /** Готовая строка значения справа. */
  readonly valueLabel: string;
}

/** Горизонтальные полосы долей — без библиотеки графиков, ширина в процентах. */
export function ShareBars({
  items,
  group,
}: {
  readonly items: readonly ShareItem[];
  readonly group: ReportGroup;
}) {
  return (
    <ul className="flex flex-col gap-3">
      {items.map((item) => (
        <li key={item.key} className="flex flex-col gap-1.5">
          <div className="flex justify-between gap-3 text-sm">
            <span className="font-medium">{item.label}</span>
            <span className="font-semibold">{item.valueLabel}</span>
          </div>
          <div className="h-2.5 rounded" style={{ background: groupColor(group, "tint") }}>
            <div
              className="h-full rounded"
              style={{
                width: `${(Math.min(Math.max(item.share, 0), 1) * 100).toFixed(1)}%`,
                background: groupColor(group, "main"),
              }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}
