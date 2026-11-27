import { Link } from "@tanstack/react-router";
import { TriangleAlertIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Sparkline } from "./charts/Sparkline";
import { ReportChangeBadge } from "./ReportChangeBadge";
import type { ReportChangeView } from "./reportFormat";
import type { ReportSearch } from "./reportSearch";
import { ReportIcon } from "./ReportStubCard";
import type { ReportDefinition } from "./registry";

/** Предупреждение на карточке: «14 клиентов должны». */
export interface ReportWarning {
  /** Текст предупреждения. */
  readonly text: string;
  /** Уровень: `warning` — обратить внимание, `danger` — нужно действовать. */
  readonly level: "warning" | "danger";
}

/** Состояние сводки карточки. */
export type ReportCardState =
  | { readonly status: "loading" }
  | { readonly status: "error"; readonly onRetry: () => void }
  | {
      readonly status: "ready";
      /** Главная цифра, готовая строка. */
      readonly value: string;
      /** Изменение к прошлому периоду; `null` — прошлого периода нет. */
      readonly change: ReportChangeView | null;
      /** Значения мини-графика за последние периоды. */
      readonly spark: readonly number[];
      readonly warning?: ReportWarning;
    };

/**
 * Карточка виджета отчёта [report]: иконка, название, вопрос, главная цифра, изменение,
 * мини-график и предупреждение. Готовая карточка — ссылка на экран отчёта с фильтрами [search].
 */
export function ReportCard({
  report,
  search,
  state,
}: {
  readonly report: ReportDefinition;
  readonly search: ReportSearch;
  readonly state: ReportCardState;
}) {
  const { t } = useI18n();
  const header = (
    <div className="flex items-start gap-3">
      <ReportIcon report={report} />
      <div className="min-w-0 flex-1">
        <h3 className="font-semibold">{t(`reports.${report.id}.name`)}</h3>
        <p className="text-muted-foreground text-sm">{t(`reports.${report.id}.question`)}</p>
      </div>
    </div>
  );
  const className = "bg-card flex flex-col gap-3 rounded-xl border p-4";
  switch (state.status) {
    case "loading":
      return (
        <article className={className} aria-busy="true">
          {header}
          <Skeleton className="h-8 w-32" />
        </article>
      );
    case "error":
      return (
        <article className={className}>
          {header}
          <div className="flex items-center justify-between gap-3 text-sm">
            <span className="text-muted-foreground">{t("reports.card.failed")}</span>
            <Button variant="outline" size="sm" onClick={state.onRetry}>
              {t("reports.card.retry")}
            </Button>
          </div>
        </article>
      );
    case "ready":
      return (
        <Link
          to="/reports/$reportId"
          params={{ reportId: report.id }}
          search={search}
          className={cn(
            className,
            "hover:border-ring/50 focus-visible:ring-ring/50 transition-colors outline-none focus-visible:ring-[3px]",
          )}
        >
          {header}
          <div className="flex items-end justify-between gap-3">
            <div className="flex flex-col gap-1">
              <span className="text-2xl font-bold tracking-tight">{state.value}</span>
              {state.change !== null && <ReportChangeBadge change={state.change} />}
            </div>
            <Sparkline values={state.spark} group={report.group} />
          </div>
          {state.warning !== undefined && (
            <span
              className={cn(
                "inline-flex items-center gap-1.5 self-start rounded-full px-2.5 py-1 text-xs font-medium",
                state.warning.level === "danger"
                  ? "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300"
                  : "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
              )}
            >
              <TriangleAlertIcon aria-hidden className="size-3.5" />
              {state.warning.text}
            </span>
          )}
        </Link>
      );
  }
}
