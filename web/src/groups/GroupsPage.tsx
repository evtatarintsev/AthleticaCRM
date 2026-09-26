import { Link } from "@tanstack/react-router";
import { PlusIcon, SearchIcon, SlidersHorizontalIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { GroupListItem, ScheduleSlot } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type I18n } from "@/i18n/context";
import { WEEK_DAYS } from "@/lib/localDate";
import { useSession } from "@/query/session";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { slotsToCards } from "./groupSchedule";
import {
  activeFilterCount,
  groupListFilters,
  groupListRequest,
  searchOf,
  type GroupListFilters,
  type GroupListSearch,
} from "./groupListSearch";
import { useGroupDisciplines, useGroupEmployees, useGroups } from "./groupsQueries";

/** Задержка между вводом в поиск и запросом к серверу, мс. */
const SEARCH_DEBOUNCE_MS = 400;

/**
 * Список групп: поиск по названию, фильтры по дисциплинам и тренерам в адресе страницы,
 * таблица с расписанием и составом тренеров, создание новой группы.
 */
export function GroupsPage({
  api,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly search: GroupListSearch;
  readonly onSearchChange: (search: GroupListSearch) => void;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const filters = useMemo(() => groupListFilters(search), [search]);
  const request = useMemo(() => groupListRequest(filters), [filters]);
  const groups = useGroups(api, branchId, request);
  const disciplines = useGroupDisciplines(api, branchId);
  const employees = useGroupEmployees(api, branchId);

  const [filtersOpen, setFiltersOpen] = useState<"disciplines" | "employees" | null>(null);
  const [query, setQuery] = useState(filters.q);
  const [syncedQ, setSyncedQ] = useState(filters.q);
  if (filters.q !== syncedQ) {
    setSyncedQ(filters.q);
    setQuery(filters.q);
  }

  useEffect(() => {
    if (query === filters.q) {
      return;
    }
    const timer = setTimeout(() => {
      onSearchChange(searchOf({ ...filters, q: query }));
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [query, filters, onSearchChange]);

  const setFilters = useCallback(
    (next: GroupListFilters) => {
      onSearchChange(searchOf(next));
    },
    [onSearchChange],
  );

  const filterCount = activeFilterCount(filters);
  const filtered = filters.q !== "" || filterCount > 0;

  return (
    <section className="pb-24">
      <PageHeader
        title={t("groups.title")}
        actions={
          <Button asChild>
            <Link to="/groups/new">
              <PlusIcon aria-hidden />
              {t("groups.create")}
            </Link>
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <SearchIcon
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
            }}
            placeholder={t("groups.search")}
            aria-label={t("groups.search")}
            className="pl-9"
          />
        </div>
        <Button
          variant="outline"
          onClick={() => {
            setFiltersOpen("disciplines");
          }}
        >
          <SlidersHorizontalIcon aria-hidden />
          {t("groups.filter.disciplines")}
          {filters.disciplineIds.length > 0 && (
            <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
              {filters.disciplineIds.length}
            </span>
          )}
        </Button>
        <Button
          variant="outline"
          onClick={() => {
            setFiltersOpen("employees");
          }}
        >
          <SlidersHorizontalIcon aria-hidden />
          {t("groups.filter.employees")}
          {filters.employeeIds.length > 0 && (
            <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
              {filters.employeeIds.length}
            </span>
          )}
        </Button>
      </div>

      {(groups.isPending || disciplines.isPending || employees.isPending) && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}
      {(groups.isError || disciplines.isError || employees.isError) && (
        <FormAlert message={t("groups.loadError")} />
      )}
      {groups.data?.groups.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">
          {t(filtered ? "groups.noResults" : "groups.empty")}
        </p>
      )}
      {groups.data !== undefined && groups.data.groups.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("groups.column.name")}</TableHead>
              <TableHead>{t("groups.column.schedule")}</TableHead>
              <TableHead>{t("groups.column.coaches")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {groups.data.groups.map((group) => (
              <GroupRow key={group.id} group={group} />
            ))}
          </TableBody>
        </Table>
      )}

      <MultiSelectPicker
        open={filtersOpen === "disciplines"}
        title={t("groups.filter.disciplines")}
        items={disciplines.data ?? []}
        selected={filters.disciplineIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setFiltersOpen(open ? "disciplines" : null);
        }}
        onApply={(disciplineIds) => {
          setFilters({ ...filters, disciplineIds });
        }}
      />
      <MultiSelectPicker
        open={filtersOpen === "employees"}
        title={t("groups.filter.employees")}
        items={employees.data ?? []}
        selected={filters.employeeIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setFiltersOpen(open ? "employees" : null);
        }}
        onApply={(employeeIds) => {
          setFilters({ ...filters, employeeIds });
        }}
      />
    </section>
  );
}

/** Строка группы [group]: название со ссылкой на карточку, расписание, состав тренеров. */
function GroupRow({ group }: { readonly group: GroupListItem }) {
  const { t } = useI18n();
  const summary = scheduleSummary(t, group.schedule);
  return (
    <TableRow>
      <TableCell>
        <Link
          to="/groups/$groupId"
          params={{ groupId: group.id }}
          className="font-medium hover:underline"
        >
          {group.name}
        </Link>
        {group.scheduleChangeAt !== null && (
          <p className="text-xs text-muted-foreground">
            {t("groups.scheduleChangeNote", { date: group.scheduleChangeAt })}
          </p>
        )}
      </TableCell>
      <TableCell className="text-muted-foreground">{summary ?? t("groups.noSchedule")}</TableCell>
      <TableCell className="text-muted-foreground">
        {group.employees.map((e) => e.name).join(", ")}
      </TableCell>
    </TableRow>
  );
}

/** Короткая сводка расписания [schedule] по карточкам: дни, время, карточки через «; ». */
function scheduleSummary(t: I18n["t"], schedule: readonly ScheduleSlot[]): string | null {
  const cards = slotsToCards(schedule);
  if (cards.length === 0) {
    return null;
  }
  return cards
    .map((card) => {
      const days = WEEK_DAYS.filter((day) => card.days.has(day))
        .map((day) => t(`day.${day}`))
        .join(" ");
      return `${days} ${card.startAt}–${card.endAt}`;
    })
    .join("; ");
}
