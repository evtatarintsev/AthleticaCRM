import { useI18n } from "@/i18n/context";
import { groupColor } from "./charts/colors";
import type { ReportDefinition } from "./registry";

/** Иконка отчёта [report] на плашке цвета его группы. */
export function ReportIcon({
  report,
  size = "md",
}: {
  readonly report: ReportDefinition;
  readonly size?: "md" | "lg";
}) {
  const Icon = report.icon;
  return (
    <span
      aria-hidden="true"
      className={
        size === "lg"
          ? "flex size-12 shrink-0 items-center justify-center rounded-xl"
          : "flex size-10 shrink-0 items-center justify-center rounded-lg"
      }
      style={{ background: groupColor(report.group, "tint"), color: groupColor(report.group) }}
    >
      <Icon className={size === "lg" ? "size-6" : "size-5"} />
    </span>
  );
}

/** Заглушка отчёта [report], который ещё не готов: название, вопрос и «скоро»; не ссылка. */
export function ReportStubCard({ report }: { readonly report: ReportDefinition }) {
  const { t } = useI18n();
  return (
    <article className="bg-card flex flex-col gap-3 rounded-xl border p-4">
      <div className="flex items-start gap-3">
        <ReportIcon report={report} />
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold">{t(`reports.${report.id}.name`)}</h3>
          <p className="text-muted-foreground text-sm">{t(`reports.${report.id}.question`)}</p>
        </div>
        <span className="bg-muted text-muted-foreground shrink-0 rounded-full px-2 py-0.5 text-xs font-medium">
          {t("reports.soon")}
        </span>
      </div>
    </article>
  );
}
