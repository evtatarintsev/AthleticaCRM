import { useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { hasPermission, useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { groupColor } from "./charts/colors";
import { ReportFilters } from "./ReportFilters";
import { formatRange } from "./reportFormat";
import { reportFilter, type ReportSearch } from "./reportSearch";
import { ReportsTable } from "./ReportsTable";
import { reportsTableQuery } from "./reportsTableQuery";
import { ReportStubCard } from "./ReportStubCard";
import { visibleGroups } from "./registry";

/**
 * Раздел «Отчёты»: карточки отчётов по группам или таблица показателей, общие фильтры периода
 * и филиала. Без права `CAN_VIEW_REPORTS` — «нет доступа» без запросов к API.
 */
export function ReportsPage({
  api,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly search: ReportSearch;
  readonly onSearchChange: (search: ReportSearch) => void;
}) {
  const { t } = useI18n();
  const session = useSession(api);
  if (!hasPermission(session, "CAN_VIEW_REPORTS")) {
    return (
      <section>
        <PageHeader title={t("reports.title")} />
        <p>{t("reports.noAccess")}</p>
      </section>
    );
  }
  return (
    <ReportsContent
      api={api}
      search={search}
      onSearchChange={onSearchChange}
      permissions={session.permissions}
      branchId={session.currentBranch.id}
    />
  );
}

/** Содержимое раздела для сотрудника с правом на отчёты. */
function ReportsContent({
  api,
  search,
  onSearchChange,
  permissions,
  branchId,
}: {
  readonly api: ApiClient;
  readonly search: ReportSearch;
  readonly onSearchChange: (search: ReportSearch) => void;
  readonly permissions: Parameters<typeof visibleGroups>[0];
  readonly branchId: Parameters<typeof reportsTableQuery>[1];
}) {
  const i18n = useI18n();
  const { t } = i18n;
  const filter = reportFilter(search);
  const periods = useQuery(reportsTableQuery(api, branchId, filter)).data;
  return (
    <section className="max-w-7xl min-w-0 space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <PageHeader
          title={t("reports.title")}
          {...(periods === undefined
            ? {}
            : {
                description: t("reports.caption", {
                  period: formatRange(i18n, periods.period),
                  base: formatRange(i18n, periods.basePeriod),
                }),
              })}
        />
        <div className="flex flex-wrap items-end gap-3">
          <ReportFilters api={api} search={search} onSearchChange={onSearchChange} />
          <div
            role="group"
            aria-label={t("reports.view.label")}
            className="bg-muted inline-flex rounded-lg p-1"
          >
            {(["cards", "table"] as const).map((view) => (
              <button
                key={view}
                type="button"
                aria-pressed={search.view === view}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium",
                  search.view === view ? "bg-background shadow-sm" : "text-muted-foreground",
                )}
                onClick={() => {
                  onSearchChange({ ...search, view });
                }}
              >
                {t(`reports.view.${view}`)}
              </button>
            ))}
          </div>
        </div>
      </div>
      {search.view === "table" ? (
        <ReportsTable api={api} search={search} />
      ) : (
        visibleGroups(permissions).map(({ group, reports }) => (
          <section key={group} aria-labelledby={`reports-group-${group}`} className="space-y-3">
            <h2
              id={`reports-group-${group}`}
              className="text-xs font-bold tracking-wide uppercase"
              style={{ color: groupColor(group) }}
            >
              {t(`reports.group.${group}`)}
            </h2>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {reports.map((report) =>
                report.impl === undefined ? (
                  <ReportStubCard key={report.id} report={report} />
                ) : (
                  <report.impl.Card
                    key={report.id}
                    api={api}
                    filter={filter}
                    search={search}
                    onSearchChange={onSearchChange}
                  />
                ),
              )}
            </div>
          </section>
        ))
      )}
    </section>
  );
}
