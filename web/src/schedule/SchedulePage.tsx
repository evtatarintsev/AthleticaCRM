import {
  ChevronLeftIcon,
  ChevronRightIcon,
  PlusIcon,
  SlidersHorizontalIcon,
  XIcon,
} from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { LocalDate } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { mondayOf, weekDates as weekDatesOf } from "@/lib/localDate";
import { useInitialMediaQuery, WIDE_SCREEN } from "@/lib/useInitialMediaQuery";
import { useNow } from "@/lib/useNow";
import { cn } from "@/lib/utils";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { CreateSessionSheet } from "./CreateSessionSheet";
import { ScheduleFilterSheet, type ScheduleFilterSection } from "./ScheduleFilterSheet";
import { ScheduleDayView, ScheduleWeekList } from "./ScheduleSessionList";
import { ScheduleWeekGrid } from "./ScheduleWeekGrid";
import {
  useOrgTimeZone,
  useScheduleDisciplines,
  useScheduleEmployees,
  useScheduleGroups,
  useScheduleHalls,
  useSchedule,
} from "./scheduleQueries";
import { longDayLabel, orgNowOf, weekRangeLabel } from "./scheduleTime";
import {
  activeScheduleFilterCount,
  dateForView,
  scheduleFiltersOf,
  scheduleListRequest,
  scheduleSearchOf,
  scheduleViewOf,
  selectedDateOf,
  shiftedDate,
  type SchedulePeriod,
  type ScheduleFilters,
  type ScheduleSearch,
  type ScheduleView,
} from "./scheduleWeek";

/** Свойства страницы расписания: адрес [search] и его смена [onSearchChange]. */
interface SchedulePageProps {
  readonly api: ApiClient;
  readonly search: ScheduleSearch;
  readonly onSearchChange: (search: ScheduleSearch) => void;
}

/**
 * Расписание в режимах «День» и «Неделя» с фильтрами по дисциплинам, залам и тренерам.
 * Режим, дата и фильтры хранятся в адресе страницы. «Сейчас» и «сегодня» считаются в часовом
 * поясе организации, поэтому до его загрузки расписание не показывается.
 */
export function SchedulePage(props: SchedulePageProps) {
  const { t } = useI18n();
  const branchId = useSession(props.api).currentBranch.id;
  const timeZone = useOrgTimeZone(props.api, branchId);
  return (
    <section className="space-y-4 pb-24 md:pb-16">
      {timeZone.data === undefined && <PageHeader title={t("schedule.title")} />}
      {timeZone.isPending && <Skeleton className="h-64 w-full" />}
      {timeZone.isError && (
        <LoadError
          onRetry={() => {
            void timeZone.refetch();
          }}
        />
      )}
      {timeZone.data !== undefined && <ScheduleBody {...props} timeZone={timeZone.data} />}
    </section>
  );
}

/** Ошибка загрузки расписания с повтором [onRetry]. */
function LoadError({ onRetry }: { readonly onRetry: () => void }) {
  const { t } = useI18n();
  return (
    <div className="space-y-2">
      <FormAlert message={t("schedule.loadError")} />
      <Button variant="outline" onClick={onRetry}>
        {t("action.retry")}
      </Button>
    </div>
  );
}

/** Расписание при известном часовом поясе организации [timeZone]. */
function ScheduleBody({
  api,
  search,
  onSearchChange,
  timeZone,
}: SchedulePageProps & { readonly timeZone: string }) {
  const { t, locale } = useI18n();
  const branch = useSession(api).currentBranch;
  const branchId = branch.id;
  const wide = useInitialMediaQuery(WIDE_SCREEN);
  const now = orgNowOf(useNow(), timeZone);
  const view = scheduleViewOf(search, wide);
  const date = selectedDateOf(search, now.date);
  const weekStart = mondayOf(date);
  const filters = scheduleFiltersOf(search);
  const weekDates = weekDatesOf(weekStart);

  const schedule = useSchedule(api, branchId, scheduleListRequest(weekStart, filters));
  const disciplines = useScheduleDisciplines(api, branchId);
  const halls = useScheduleHalls(api, branchId);
  const employees = useScheduleEmployees(api, branchId);
  const groups = useScheduleGroups(api, branchId, filters.groupIds.length > 0);

  const [filterSection, setFilterSection] = useState<ScheduleFilterSection | null>(null);
  const [createDate, setCreateDate] = useState<LocalDate | null>(null);

  const goTo = (period: SchedulePeriod) => {
    onSearchChange(scheduleSearchOf(period, filters));
  };
  const setFilters = (next: ScheduleFilters) => {
    onSearchChange(scheduleSearchOf(search, next));
  };
  const switchView = (next: ScheduleView) => {
    goTo({ view: next, date: dateForView(view, next, date, now.date) });
  };
  const dayLink = (day: LocalDate) => scheduleSearchOf({ view: "day", date: day }, filters);

  const isDay = view === "day";
  const away = isDay ? date !== now.date : weekStart !== mondayOf(now.date);
  const dayLabel = longDayLabel(locale, date);
  const periodLabel = isDay
    ? date === now.date
      ? t("schedule.dayLabelToday", { day: dayLabel })
      : dayLabel
    : weekRangeLabel(locale, weekStart);
  const filterCount = activeScheduleFilterCount(filters);
  const selectedCount =
    filters.disciplineIds.length + filters.hallIds.length + filters.employeeIds.length;
  const sessions = schedule.data?.sessions ?? [];
  const emptyText = t(filterCount > 0 ? "schedule.emptyFiltered" : "schedule.empty");
  const shown = isDay ? sessions.filter((s) => s.date === date) : sessions;
  const cancelledCount = shown.filter((s) => s.status === "CANCELLED").length;
  const addSession = () => {
    setCreateDate(date);
  };

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("schedule.title")}</h1>
          <p className="mt-1 flex flex-wrap gap-x-3.5 text-sm">
            <span>{t("schedule.branch", { name: branch.name })}</span>
            {schedule.data !== undefined && (
              <span className="text-muted-foreground">
                {t(isDay ? "schedule.meta.day" : "schedule.meta.week", {
                  count: shown.length - cancelledCount,
                })}
                {cancelledCount > 0 &&
                  ` · ${t("schedule.meta.cancelled", { count: cancelledCount })}`}
              </span>
            )}
          </p>
        </div>
        <Button className="hidden md:inline-flex" onClick={addSession}>
          <PlusIcon aria-hidden />
          {t("schedule.addSessionAction")}
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-x-3 gap-y-2.5">
        <div className="flex w-full items-center gap-1.5 md:w-auto">
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t(isDay ? "schedule.prevDay" : "schedule.prevWeek")}
            onClick={() => {
              goTo({ view: search.view, date: shiftedDate(view, date, -1) });
            }}
          >
            <ChevronLeftIcon aria-hidden />
          </Button>
          <span className="flex-1 px-1.5 text-center font-semibold whitespace-nowrap tabular-nums md:flex-none">
            {periodLabel}
          </span>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t(isDay ? "schedule.nextDay" : "schedule.nextWeek")}
            onClick={() => {
              goTo({ view: search.view, date: shiftedDate(view, date, 1) });
            }}
          >
            <ChevronRightIcon aria-hidden />
          </Button>
          {away && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                goTo({ view: search.view });
              }}
            >
              {t("schedule.today")}
            </Button>
          )}
        </div>
        <div
          role="group"
          aria-label={t("schedule.view")}
          className="inline-flex gap-0.5 rounded-lg border bg-muted/60 p-[3px]"
        >
          {(["day", "week"] as const).map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={view === option}
              className={cn(
                "h-7 rounded-md px-3 text-sm font-medium text-muted-foreground",
                view === option && "bg-background text-foreground shadow-sm",
              )}
              onClick={() => {
                switchView(option);
              }}
            >
              {t(`schedule.view.${option}`)}
            </button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {filters.groupIds.map((groupId) => (
            <GroupFilterChip
              key={groupId}
              name={groups.data?.find((group) => group.id === groupId)?.name ?? null}
              onRemove={() => {
                setFilters({
                  ...filters,
                  groupIds: filters.groupIds.filter((id) => id !== groupId),
                });
              }}
            />
          ))}
          <FilterPill
            className="hidden md:inline-flex"
            label={t("schedule.filter.disciplines")}
            count={filters.disciplineIds.length}
            onClick={() => {
              setFilterSection("disciplines");
            }}
          />
          <FilterPill
            className="hidden md:inline-flex"
            label={t("schedule.filter.halls")}
            count={filters.hallIds.length}
            onClick={() => {
              setFilterSection("halls");
            }}
          />
          <FilterPill
            className="hidden md:inline-flex"
            label={t("schedule.filter.employees")}
            count={filters.employeeIds.length}
            onClick={() => {
              setFilterSection("employees");
            }}
          />
          <FilterPill
            className="md:hidden"
            icon
            label={t("schedule.filters")}
            count={selectedCount}
            onClick={() => {
              setFilterSection("disciplines");
            }}
          />
          {filterCount > 0 && (
            <Button
              variant="link"
              size="sm"
              className="px-1"
              onClick={() => {
                setFilters({ disciplineIds: [], hallIds: [], employeeIds: [], groupIds: [] });
              }}
            >
              {t("schedule.filter.reset")}
            </Button>
          )}
        </div>
      </div>

      {schedule.isPending && <Skeleton className="h-64 w-full" />}
      {schedule.isError && (
        <LoadError
          onRetry={() => {
            void schedule.refetch();
          }}
        />
      )}
      {schedule.data !== undefined &&
        (isDay ? (
          <ScheduleDayView
            weekDates={weekDates}
            sessions={sessions}
            date={date}
            now={now}
            emptyText={t(filterCount > 0 ? "schedule.emptyFiltered" : "schedule.dayEmpty")}
            dayLink={dayLink}
            onAddSession={setCreateDate}
          />
        ) : (
          <>
            <div className="hidden md:block">
              <ScheduleWeekGrid
                weekDates={weekDates}
                sessions={sessions}
                now={now}
                emptyText={emptyText}
                dayLink={dayLink}
                onAddSession={setCreateDate}
              />
            </div>
            <div className="md:hidden">
              <ScheduleWeekList
                weekDates={weekDates}
                sessions={sessions}
                now={now}
                emptyText={emptyText}
              />
            </div>
          </>
        ))}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t bg-background px-4 pt-2.5 pb-[calc(0.625rem+env(safe-area-inset-bottom))] md:hidden">
        <Button className="w-full" onClick={addSession}>
          <PlusIcon aria-hidden />
          {t("schedule.addSessionAction")}
        </Button>
      </div>
      <CreateSessionSheet
        api={api}
        date={createDate}
        onClose={() => {
          setCreateDate(null);
        }}
      />
      <ScheduleFilterSheet
        section={filterSection}
        disciplines={disciplines.data ?? []}
        halls={halls.data ?? []}
        employees={employees.data ?? []}
        selected={filters}
        onClose={() => {
          setFilterSection(null);
        }}
        onApply={(next) => {
          setFilters({ ...filters, ...next });
        }}
      />
    </>
  );
}

/**
 * Кнопка фильтра [label] с бейджем количества выбранных значений [count]; [icon] — значок
 * фильтров перед подписью (одна общая кнопка на узком экране).
 */
function FilterPill({
  label,
  count,
  icon = false,
  className,
  onClick,
}: {
  readonly label: string;
  readonly count: number;
  readonly icon?: boolean;
  readonly className?: string;
  readonly onClick: () => void;
}) {
  return (
    <Button
      variant="outline"
      size="sm"
      className={cn(count > 0 && "border-primary bg-primary/10 text-primary", className)}
      onClick={onClick}
    >
      {icon && <SlidersHorizontalIcon aria-hidden />}
      {label}
      {count > 0 && (
        <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
          {count}
        </span>
      )}
    </Button>
  );
}

/**
 * Снимаемый чип активного фильтра по группе [name]; `null` — группы нет в списке
 * филиала (или он ещё загружается), показывается общая подпись.
 */
function GroupFilterChip({
  name,
  onRemove,
}: {
  readonly name: string | null;
  readonly onRemove: () => void;
}) {
  const { t } = useI18n();
  return (
    <span className="inline-flex h-8 items-center gap-1 rounded-lg border border-primary bg-primary/10 pr-1 pl-3 text-sm font-medium text-primary">
      {name === null ? t("schedule.filter.groupUnknown") : t("schedule.filter.group", { name })}
      <button
        type="button"
        aria-label={t("schedule.filter.removeGroup")}
        onClick={onRemove}
        className="rounded-md p-1 hover:bg-primary/15"
      >
        <XIcon aria-hidden className="size-3.5" />
      </button>
    </span>
  );
}
