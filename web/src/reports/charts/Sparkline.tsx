import { Bar, BarChart, type BarShapeProps, Rectangle } from "recharts";
import { groupColor, type ReportGroup } from "./colors";

/** Свойства [Sparkline]. */
export interface SparklineProps {
  /** Значения за последние периоды, последнее — текущий. */
  readonly values: readonly number[];
  /** Группа отчёта: задаёт цвет. */
  readonly group: ReportGroup;
  /** Ширина в пикселях. */
  readonly width?: number;
  /** Высота в пикселях. */
  readonly height?: number;
}

/**
 * Мини-график карточки: столбцы без осей и подсказок, текущий период — основным цветом группы.
 * Скрыт от экранных читалок: цифра и изменение рядом уже есть текстом.
 */
export function Sparkline({ values, group, width = 96, height = 32 }: SparklineProps) {
  const data = values.map((value, index) => ({ index, value }));
  const last = values.length - 1;
  return (
    <div aria-hidden="true" data-testid="sparkline">
      <BarChart
        data={data}
        width={width}
        height={height}
        margin={{ top: 0, right: 0, bottom: 0, left: 0 }}
        accessibilityLayer={false}
      >
        <Bar
          dataKey="value"
          radius={[2, 2, 0, 0]}
          isAnimationActive={false}
          shape={(props: BarShapeProps) => (
            <Rectangle {...props} fill={groupColor(group, props.index === last ? "main" : "mid")} />
          )}
        />
      </BarChart>
    </div>
  );
}
