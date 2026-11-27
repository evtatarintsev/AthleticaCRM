import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { ExportFormat, ReportsTableRow } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { downloadBlob } from "@/lib/download";
import { apiErrorOf } from "@/query/apiFailure";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { groupColor } from "./charts/colors";
import { ReportChangeBadge } from "./ReportChangeBadge";
import { formatRange, formatReportValue, reportChangeView } from "./reportFormat";
import { reportFilter, type ReportSearch } from "./reportSearch";
import { reportById, reportGroups } from "./registry";
import { reportsTableQuery } from "./reportsTableQuery";

/** Форматы выгрузки в порядке кнопок. */
const exportFormats: readonly ExportFormat[] = ["xlsx", "csv"];

/**
 * Таблица показателей отчётов за период и прошлый период рядом, по группам; выгрузка в Excel
 * или CSV тем же фильтром. Пока строк нет — пустое состояние, выгрузка недоступна.
 */
export function ReportsTable({
  api,
  search,
}: {
  readonly api: ApiClient;
  readonly search: ReportSearch;
}) {
  const i18n = useI18n();
  const { t } = i18n;
  const branchId = useSession(api).currentBranch.id;
  const filter = reportFilter(search);
  const table = useQuery(reportsTableQuery(api, branchId, filter));
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  if (table.isPending) {
    return <Skeleton className="h-40 w-full" />;
  }
  if (table.isError) {
    return <FormAlert message={apiErrorMessage(t, apiErrorOf(table.error))} />;
  }
  const { rows, period, basePeriod } = table.data;
  const download = async (format: ExportFormat) => {
    setExporting(format);
    setFailure(null);
    const result = await api.call("reports/table/export", { filter, format });
    setExporting(null);
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    downloadBlob(`reports-${period.from}-${period.to}.${format}`, result.value);
  };
  const grouped: readonly {
    readonly group: (typeof reportGroups)[number] | null;
    readonly rows: readonly ReportsTableRow[];
  }[] = [
    ...reportGroups.map((group) => ({
      group,
      rows: rows.filter((row) => reportById(row.reportId)?.group === group),
    })),
    { group: null, rows: rows.filter((row) => reportById(row.reportId) === undefined) },
  ].filter((entry) => entry.rows.length > 0);
  return (
    <div className="space-y-3">
      <div className="bg-card overflow-x-auto rounded-xl border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left">
              <th className="p-3 font-medium">{t("reports.table.metric")}</th>
              <th className="p-3 text-right font-medium">{formatRange(i18n, period)}</th>
              <th className="p-3 text-right font-medium">{formatRange(i18n, basePeriod)}</th>
              <th className="p-3 text-right font-medium">{t("reports.table.change")}</th>
            </tr>
          </thead>
          <tbody>
            {grouped.length === 0 ? (
              <tr>
                <td colSpan={4} className="text-muted-foreground p-6 text-center">
                  {t("reports.table.empty")}
                </td>
              </tr>
            ) : (
              grouped.map(({ group, rows: groupRows }) => (
                <TableGroup key={group ?? "other"} group={group} rows={groupRows} />
              ))
            )}
          </tbody>
        </table>
      </div>
      {failure !== null && <FormAlert message={failure} />}
      <div className="flex justify-end gap-2">
        {exportFormats.map((format) => (
          <Button
            key={format}
            variant="outline"
            disabled={rows.length === 0 || exporting !== null}
            onClick={() => {
              void download(format);
            }}
          >
            {t(`reports.export.${format}`)}
          </Button>
        ))}
      </div>
    </div>
  );
}

/**
 * Заголовок группы [group] таблицы показателей и её строки [rows]; строки отчётов, которых
 * нет в реестре (`group` — `null`), идут последними без заголовка, как и в файле выгрузки.
 */
function TableGroup({
  group,
  rows,
}: {
  readonly group: (typeof reportGroups)[number] | null;
  readonly rows: readonly ReportsTableRow[];
}) {
  const i18n = useI18n();
  return (
    <>
      {group !== null && (
        <tr>
          <td
            colSpan={4}
            className="px-3 pt-4 pb-1 text-xs font-bold tracking-wide uppercase"
            style={{ color: groupColor(group) }}
          >
            {i18n.t(`reports.group.${group}`)}
          </td>
        </tr>
      )}
      {rows.map((row) => {
        const change = reportChangeView(i18n, row.value, row.base, row.better);
        return (
          <tr key={`${row.reportId}-${row.label}`} className="border-t">
            <td className="p-3">{row.label}</td>
            <td className="p-3 text-right font-semibold">{formatReportValue(i18n, row.value)}</td>
            <td className="text-muted-foreground p-3 text-right">
              {row.base === null ? "—" : formatReportValue(i18n, row.base)}
            </td>
            <td className="p-3 text-right">
              {change !== null && <ReportChangeBadge change={change} />}
            </td>
          </tr>
        );
      })}
    </>
  );
}
