import { useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import {
  BranchIdSchema,
  LocalDateSchema,
  ReportCompareModeSchema,
  ReportPeriodKindSchema,
  type LocalDate,
} from "@/api/generated/contracts";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { myBranchesQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { isCustomPeriodValid, type ReportSearch } from "./reportSearch";

/**
 * Фильтры отчётов: период (свой период — две даты и режим сравнения) и филиал из доступных
 * сотруднику. Дата, с которой начало периода оказалось бы позже конца, период — длиннее
 * допустимого или конец — в будущем, не применяется.
 */
export function ReportFilters({
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
  const branches = useQuery(myBranchesQuery(api)).data?.branches ?? [];
  const today = todayLocalDate(session.orgInfo.timezone);
  const setDates = (from: LocalDate | undefined, to: LocalDate | undefined) => {
    if (from !== undefined && to !== undefined && isCustomPeriodValid(from, to) && to <= today) {
      onSearchChange({ ...search, from, to });
    }
  };
  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground block">{t("reports.period.label")}</span>
        <NativeSelect
          value={search.period}
          onChange={(e) => {
            const period = ReportPeriodKindSchema.catch("month").parse(e.target.value);
            onSearchChange(
              period === "custom"
                ? {
                    ...search,
                    period,
                    from: search.from ?? LocalDateSchema.parse(`${today.slice(0, 7)}-01`),
                    to: search.to ?? today,
                  }
                : { ...search, period, from: undefined, to: undefined },
            );
          }}
        >
          {ReportPeriodKindSchema.options.map((period) => (
            <option key={period} value={period}>
              {t(`reports.period.${period}`)}
            </option>
          ))}
        </NativeSelect>
      </label>
      {search.period === "custom" && (
        <>
          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground block">{t("reports.from")}</span>
            <Input
              type="date"
              max={search.to ?? today}
              value={search.from ?? ""}
              onChange={(e) => {
                setDates(LocalDateSchema.safeParse(e.target.value).data, search.to);
              }}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground block">{t("reports.to")}</span>
            <Input
              type="date"
              min={search.from}
              max={today}
              value={search.to ?? ""}
              onChange={(e) => {
                setDates(search.from, LocalDateSchema.safeParse(e.target.value).data);
              }}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground block">{t("reports.compare.label")}</span>
            <NativeSelect
              value={search.compare}
              onChange={(e) => {
                onSearchChange({
                  ...search,
                  compare: ReportCompareModeSchema.catch("days-before").parse(e.target.value),
                });
              }}
            >
              {ReportCompareModeSchema.options.map((mode) => (
                <option key={mode} value={mode}>
                  {t(`reports.compare.${mode}`)}
                </option>
              ))}
            </NativeSelect>
          </label>
        </>
      )}
      {branches.length > 1 && (
        <label className="space-y-1 text-sm">
          <span className="text-muted-foreground block">{t("reports.branch.label")}</span>
          <NativeSelect
            value={search.branch ?? ""}
            onChange={(e) => {
              onSearchChange({ ...search, branch: BranchIdSchema.safeParse(e.target.value).data });
            }}
          >
            <option value="">{t("reports.branch.all")}</option>
            {branches.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </NativeSelect>
        </label>
      )}
    </div>
  );
}
