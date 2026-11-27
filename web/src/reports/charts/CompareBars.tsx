import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis } from "recharts";
import { ChartLegend } from "./ChartLegend";
import { ChartTooltip } from "./ChartTooltip";
import { groupColor, type ReportGroup } from "./colors";

/** Пара столбцов «период / прошлый период». */
export interface ComparePoint {
  /** Уникальный ключ пары. */
  readonly key: string;
  /** Подпись под парой. */
  readonly label: string;
  /** Значение за период. */
  readonly value: number;
  /** Значение за прошлый период. */
  readonly base: number;
  /** Готовая строка подсказки. */
  readonly tooltip: string;
}

/** Свойства [CompareBars]. */
export interface CompareBarsProps {
  /** Пары столбцов в порядке показа. */
  readonly data: readonly ComparePoint[];
  /** Группа отчёта: задаёт цвет. */
  readonly group: ReportGroup;
  /** Подпись периода в легенде («октябрь»). */
  readonly valueLabel: string;
  /** Подпись прошлого периода в легенде («сентябрь»). */
  readonly baseLabel: string;
  /** Текстовый пересказ графика для экранных читалок. */
  readonly ariaLabel: string;
  /** Ширина в пикселях; без неё график занимает ширину контейнера. */
  readonly width?: number;
  /** Высота в пикселях. */
  readonly height?: number;
}

/** Столбцы «период / прошлый период» рядом, с легендой. */
export function CompareBars({
  data,
  group,
  valueLabel,
  baseLabel,
  ariaLabel,
  width,
  height = 200,
}: CompareBarsProps) {
  const tooltips = new Map(data.map((point) => [point.key, point.tooltip]));
  const chart = (
    <BarChart
      data={[...data]}
      {...(width === undefined ? {} : { width, height })}
      margin={{ top: 8, right: 0, bottom: 0, left: 0 }}
    >
      <XAxis
        dataKey="key"
        tickFormatter={(_value: unknown, index: number) => data[index]?.label ?? ""}
        tickLine={false}
        axisLine={false}
        fontSize={12}
      />
      <Tooltip
        cursor={{ fill: "var(--color-muted)" }}
        content={({ active, label }) => (
          <ChartTooltip text={active ? tooltips.get(String(label)) : undefined} />
        )}
      />
      <Bar
        dataKey="base"
        name={baseLabel}
        fill={groupColor(group, "mid")}
        radius={[3, 3, 0, 0]}
        isAnimationActive={false}
      />
      <Bar
        dataKey="value"
        name={valueLabel}
        fill={groupColor(group, "main")}
        radius={[3, 3, 0, 0]}
        isAnimationActive={false}
      />
    </BarChart>
  );
  return (
    <div className="flex flex-col gap-3">
      <ChartLegend
        items={[
          { key: "value", color: groupColor(group, "main"), label: valueLabel },
          { key: "base", color: groupColor(group, "mid"), label: baseLabel },
        ]}
      />
      <div role="img" aria-label={ariaLabel}>
        {width === undefined ? (
          <ResponsiveContainer width="100%" height={height}>
            {chart}
          </ResponsiveContainer>
        ) : (
          chart
        )}
      </div>
    </div>
  );
}
