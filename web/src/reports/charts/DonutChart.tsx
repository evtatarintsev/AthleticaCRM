import { Pie, PieChart } from "recharts";
import { ChartLegend } from "./ChartLegend";
import { seriesColor, type ReportGroup } from "./colors";

/** Доля кольцевой диаграммы. */
export interface DonutSlice {
  /** Уникальный ключ доли. */
  readonly key: string;
  /** Подпись в легенде. */
  readonly label: string;
  /** Значение доли. */
  readonly value: number;
  /** Готовая строка значения для легенды («312 400 ₽ · 52%»). */
  readonly valueLabel: string;
}

/** Свойства [DonutChart]. */
export interface DonutChartProps {
  /** Доли в порядке показа. */
  readonly data: readonly DonutSlice[];
  /** Группа отчёта: задаёт цвета долей. */
  readonly group: ReportGroup;
  /** Число в центре кольца (готовая строка). */
  readonly center: string;
  /** Подпись под числом в центре. */
  readonly centerCaption?: string;
  /** Текстовый пересказ диаграммы для экранных читалок. */
  readonly ariaLabel: string;
  /** Размер кольца в пикселях. */
  readonly size?: number;
}

/** Кольцевая диаграмма с числом в центре и легендой списком. */
export function DonutChart({
  data,
  group,
  center,
  centerCaption,
  ariaLabel,
  size = 160,
}: DonutChartProps) {
  return (
    <div className="flex flex-wrap items-center gap-6">
      <div role="img" aria-label={ariaLabel} className="relative shrink-0">
        <PieChart width={size} height={size} accessibilityLayer={false}>
          <Pie
            data={data.map((slice, index) => ({ ...slice, fill: seriesColor(group, index) }))}
            dataKey="value"
            nameKey="label"
            innerRadius="68%"
            outerRadius="100%"
            stroke="none"
            isAnimationActive={false}
          />
        </PieChart>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-lg font-bold">{center}</span>
          {centerCaption === undefined ? null : (
            <span className="text-muted-foreground text-xs">{centerCaption}</span>
          )}
        </div>
      </div>
      <div className="min-w-48 flex-1">
        <ChartLegend
          vertical
          items={data.map((slice, index) => ({
            key: slice.key,
            color: seriesColor(group, index),
            label: slice.label,
            value: slice.valueLabel,
          }))}
        />
      </div>
    </div>
  );
}
