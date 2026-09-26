import { ChevronLeftIcon, ChevronRightIcon, SlidersHorizontalIcon } from "lucide-react";
import { useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { LocalDate } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { addDays, weekDates as weekDatesOf } from "@/lib/localDate";
import { useSession } from "@/query/session";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { ScheduleWeekGrid } from "./ScheduleWeekGrid";
import {
  useScheduleDisciplines,
  useScheduleEmployees,
  useScheduleHalls,
  useSchedule,
} from "./scheduleQueries";
import {
  activeScheduleFilterCount,
  scheduleFiltersOf,
  scheduleListRequest,
  scheduleSearchOf,
  weekStartOf,
  type ScheduleFilters,
  type ScheduleSearch,
} from "./scheduleWeek";

/**
 * Расписание: недельная сетка занятий с выбором недели и фильтрами по дисциплинам,
 * залам и тренерам. Фильтры и неделя хранятся в адресе страницы.
 */
export function SchedulePage({
  api,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly search: ScheduleSearch;
  readonly onSearchChange: (search: ScheduleSearch) => void;
}) {
  const { t, format } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const weekStart = weekStartOf(search);
  const filters = useMemo(() => scheduleFiltersOf(search), [search]);
  const weekDates = useMemo(() => weekDatesOf(weekStart), [weekStart]);
  const request = useMemo(() => scheduleListRequest(weekStart, filters), [weekStart, filters]);

  const schedule = useSchedule(api, branchId, request);
  const disciplines = useScheduleDisciplines(api, branchId);
  const halls = useScheduleHalls(api, branchId);
  const employees = useScheduleEmployees(api, branchId);

  const [picker, setPicker] = useState<"disciplines" | "halls" | "employees" | null>(null);

  const setFilters = (next: ScheduleFilters) => {
    onSearchChange(scheduleSearchOf(weekStart, next));
  };
  const goToWeek = (nextWeekStart: LocalDate) => {
    onSearchChange(scheduleSearchOf(nextWeekStart, filters));
  };

  const filterCount = activeScheduleFilterCount(filters);
  const sessions = schedule.data?.sessions ?? [];
  const filtered = filterCount > 0;

  return (
    <section className="space-y-4 pb-16">
      <PageHeader title={t("schedule.title")} />

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          size="icon"
          aria-label={t("schedule.prevWeek")}
          onClick={() => {
            goToWeek(addDays(weekStart, -7));
          }}
        >
          <ChevronLeftIcon aria-hidden />
        </Button>
        <Button
          variant="outline"
          size="icon"
          aria-label={t("schedule.nextWeek")}
          onClick={() => {
            goToWeek(addDays(weekStart, 7));
          }}
        >
          <ChevronRightIcon aria-hidden />
        </Button>
        <span className="rounded-full border px-3 py-1 text-sm font-medium">
          {t("schedule.weekRange", {
            from: format.date(weekDates[0] ?? weekStart),
            to: format.date(weekDates[6] ?? weekStart),
          })}
        </span>

        <div className="ml-auto flex flex-wrap items-center gap-2">
          <FilterPill
            label={t("schedule.filter.disciplines")}
            count={filters.disciplineIds.length}
            onClick={() => {
              setPicker("disciplines");
            }}
          />
          <FilterPill
            label={t("schedule.filter.halls")}
            count={filters.hallIds.length}
            onClick={() => {
              setPicker("halls");
            }}
          />
          <FilterPill
            label={t("schedule.filter.employees")}
            count={filters.employeeIds.length}
            onClick={() => {
              setPicker("employees");
            }}
          />
          {filterCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setFilters({ disciplineIds: [], hallIds: [], employeeIds: [] });
              }}
            >
              {t("schedule.filter.reset")}
            </Button>
          )}
        </div>
      </div>

      {schedule.isPending && <Skeleton className="h-64 w-full" />}
      {schedule.isError && (
        <div className="space-y-2">
          <FormAlert message={t("schedule.loadError")} />
          <Button
            variant="outline"
            onClick={() => {
              void schedule.refetch();
            }}
          >
            {t("action.retry")}
          </Button>
        </div>
      )}
      {schedule.data !== undefined && sessions.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">
          {t(filtered ? "schedule.emptyFiltered" : "schedule.empty")}
        </p>
      )}
      {sessions.length > 0 && <ScheduleWeekGrid weekDates={weekDates} sessions={sessions} />}

      <MultiSelectPicker
        open={picker === "disciplines"}
        title={t("schedule.filter.disciplines")}
        items={disciplines.data ?? []}
        selected={filters.disciplineIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "disciplines" : null);
        }}
        onApply={(disciplineIds) => {
          setFilters({ ...filters, disciplineIds });
        }}
      />
      <MultiSelectPicker
        open={picker === "halls"}
        title={t("schedule.filter.halls")}
        items={halls.data ?? []}
        selected={filters.hallIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "halls" : null);
        }}
        onApply={(hallIds) => {
          setFilters({ ...filters, hallIds });
        }}
      />
      <MultiSelectPicker
        open={picker === "employees"}
        title={t("schedule.filter.employees")}
        items={employees.data ?? []}
        selected={filters.employeeIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "employees" : null);
        }}
        onApply={(employeeIds) => {
          setFilters({ ...filters, employeeIds });
        }}
      />
    </section>
  );
}

/** Кнопка-пилюля фильтра [label] с бейджем количества выбранных значений [count]. */
function FilterPill({
  label,
  count,
  onClick,
}: {
  readonly label: string;
  readonly count: number;
  readonly onClick: () => void;
}) {
  return (
    <Button variant="outline" size="sm" onClick={onClick}>
      <SlidersHorizontalIcon aria-hidden />
      {label}
      {count > 0 && (
        <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
          {count}
        </span>
      )}
    </Button>
  );
}
