import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import { buttonVariants } from "@/components/ui/button-variants";
import { useI18n } from "@/i18n/context";
import { hasPermission, useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { reportFilter, type ReportSearch } from "./reportSearch";
import { visibleReports } from "./registry";

/**
 * Экран отчёта [reportId]. Без `CAN_VIEW_REPORTS` — «нет доступа»; неизвестный отчёт,
 * заглушка или отчёт, на который не хватает прав, — «не найдено».
 */
export function ReportPage({
  api,
  reportId,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly reportId: string;
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
  const report = visibleReports(session.permissions).find((r) => r.id === reportId);
  if (report?.impl === undefined) {
    return (
      <section className="space-y-4">
        <PageHeader title={t("reports.notFound")} />
        <Link to="/reports" search={search} className={buttonVariants({ variant: "outline" })}>
          {t("reports.allReports")}
        </Link>
      </section>
    );
  }
  const { Screen } = report.impl;
  return (
    <Screen
      api={api}
      filter={reportFilter(search)}
      search={search}
      onSearchChange={onSearchChange}
    />
  );
}
