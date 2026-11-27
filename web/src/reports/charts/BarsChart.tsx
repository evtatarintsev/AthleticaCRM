import {
  Bar,
  BarChart,
  type BarShapeProps,
  Rectangle,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
} from "recharts";
import { ChartTooltip } from "./ChartTooltip";

/** Точка столбчатого графика. */
export interface BarPoint {
  /** Уникальный ключ точки (например, дата). */
  readonly key: string;
  /** Подпись под столбцом. */
  readonly label: string;
  /** Значение столбца; может быть отрицательным. */
  readonly value: number;
  /** Готовая строка подсказки (деньги и единицы уже отформатированы). */
  readonly tooltip: string;
}

/** Свойства [BarsChart]. */
export interface BarsChartProps {
  /** Столбцы в порядке показа. */
  readonly data: readonly BarPoint[];
  /** Цвет столбца по его точке. */
  readonly color: (point: BarPoint) => string;
  /** Текстовый пересказ графика для экранных читалок. */
  readonly ariaLabel: string;
  /** Ширина в пикселях; без неё график занимает ширину контейнера. */
  readonly width?: number;
  /** Высота в пикселях. */
  readonly height?: number;
}

/** Вертикальные столбцы с подписями и подсказкой: «По дням», график на плашке экрана отчёта. */
export function BarsChart({ data, color, ariaLabel, width, height = 180 }: BarsChartProps) {
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
        interval="preserveStartEnd"
        fontSize={12}
      />
      <ReferenceLine y={0} stroke="var(--color-border)" />
      <Tooltip
        cursor={{ fill: "var(--color-muted)" }}
        content={({ active, label }) => (
          <ChartTooltip text={active ? tooltips.get(String(label)) : undefined} />
        )}
      />
      <Bar
        dataKey="value"
        radius={[3, 3, 0, 0]}
        isAnimationActive={false}
        shape={(props: BarShapeProps) => {
          const point = data[props.index];
          return point === undefined ? (
            <Rectangle {...props} />
          ) : (
            <Rectangle {...props} fill={color(point)} />
          );
        }}
      />
    </BarChart>
  );
  return (
    <div role="img" aria-label={ariaLabel}>
      {width === undefined ? (
        <ResponsiveContainer width="100%" height={height}>
          {chart}
        </ResponsiveContainer>
      ) : (
        chart
      )}
    </div>
  );
}
