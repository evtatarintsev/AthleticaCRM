import { Link } from "@tanstack/react-router";
import { PlusIcon, SearchIcon, SlidersHorizontalIcon, UsersIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type { GroupListItem, ScheduleSlot } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import { useI18n } from "@/i18n/context";
import { useSession } from "@/query/session";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { GroupCreateSheet } from "./GroupCreateSheet";
import { mergedScheduleRows } from "./groupOverview";
import {
  groupListEmptyState,
  groupListFilters,
  groupListRequest,
  searchOf,
  type GroupListFilters,
  type GroupListSearch,
} from "./groupListSearch";
import { useGroupDisciplines, useGroupEmployees, useGroups } from "./groupsQueries";

/** Задержка между вводом в поиск и запросом к серверу, мс. */
const SEARCH_DEBOUNCE_MS = 400;

/** Признак открытой панели из search-параметров [search], чтобы смена фильтров её не закрывала. */
function panelOf(search: GroupListSearch): GroupListSearch {
  return search.create === true ? { create: true } : {};
}

/**
 * Список групп: поиск по названию, фильтры по дисциплинам и тренерам в адресе страницы
 * с чипами выбранных значений, таблица в карточке на компьютере и список строк на телефоне,
 * оформленные пустые состояния, создание новой группы в панели поверх списка.
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
      onSearchChange({ ...searchOf({ ...filters, q: query }), ...panelOf(search) });
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [query, filters, search, onSearchChange]);

  const setFilters = useCallback(
    (next: GroupListFilters) => {
      onSearchChange({ ...searchOf(next), ...panelOf(search) });
    },
    [search, onSearchChange],
  );

  const resetFilters = () => {
    onSearchChange(panelOf(search));
  };
  const selectedCount = filters.disciplineIds.length + filters.employeeIds.length;
  const disciplineChips = (disciplines.data ?? []).filter((d) =>
    filters.disciplineIds.includes(d.id),
  );
  const employeeChips = (employees.data ?? []).filter((e) => filters.employeeIds.includes(e.id));
  const emptyState =
    groups.data === undefined
      ? null
      : groupListEmptyState(groups.data.total, groups.data.groups.length);

  return (
    <section className="pb-24">
      <PageHeader
        title={t("groups.title")}
        description={
          groups.data === undefined || emptyState === "no-groups"
            ? undefined
            : t("groups.count", { count: groups.data.groups.length })
        }
        actions={
          <Button asChild className="max-md:w-9 max-md:has-[>svg]:px-0">
            <Link to="/groups" search={{ ...search, create: true }}>
              <PlusIcon aria-hidden />
              <span className="sr-only md:not-sr-only">{t("groups.create")}</span>
            </Link>
          </Button>
        }
      />

      {emptyState !== "no-groups" && (
        <div className="mb-4 space-y-3">
          <div className="flex items-center gap-2">
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
              className="hidden md:inline-flex"
              onClick={() => {
                setFiltersOpen("disciplines");
              }}
            >
              <SlidersHorizontalIcon aria-hidden />
              {t("groups.filter.disciplines")}
              <CountBadge count={filters.disciplineIds.length} />
            </Button>
            <Button
              variant="outline"
              className="hidden md:inline-flex"
              onClick={() => {
                setFiltersOpen("employees");
              }}
            >
              <SlidersHorizontalIcon aria-hidden />
              {t("groups.filter.employees")}
              <CountBadge count={filters.employeeIds.length} />
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="outline"
                  className="has-[>svg]:px-2.5 md:hidden"
                  aria-label={t("groups.filters")}
                >
                  <SlidersHorizontalIcon aria-hidden />
                  <CountBadge count={selectedCount} />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  onSelect={() => {
                    setFiltersOpen("disciplines");
                  }}
                >
                  {t("groups.filter.disciplines")}
                  <CountBadge count={filters.disciplineIds.length} />
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => {
                    setFiltersOpen("employees");
                  }}
                >
                  {t("groups.filter.employees")}
                  <CountBadge count={filters.employeeIds.length} />
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          {selectedCount > 0 && (
            <div className="flex flex-wrap items-center gap-2">
              {disciplineChips.map((d) => (
                <FilterChip
                  key={d.id}
                  prefix={t("groups.chip.discipline")}
                  name={d.name}
                  onRemove={() => {
                    setFilters({
                      ...filters,
                      disciplineIds: filters.disciplineIds.filter((id) => id !== d.id),
                    });
                  }}
                />
              ))}
              {employeeChips.map((e) => (
                <FilterChip
                  key={e.id}
                  prefix={t("groups.chip.employee")}
                  name={e.name}
                  onRemove={() => {
                    setFilters({
                      ...filters,
                      employeeIds: filters.employeeIds.filter((id) => id !== e.id),
                    });
                  }}
                />
              ))}
              <Button variant="link" size="sm" className="px-1" onClick={resetFilters}>
                {t("groups.resetFilters")}
              </Button>
            </div>
          )}
        </div>
      )}

      {(groups.isPending || disciplines.isPending || employees.isPending) && (
        <div className="space-y-3 rounded-2xl border bg-card p-4 shadow-sm">
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
      )}
      {(groups.isError || disciplines.isError || employees.isError) && (
        <FormAlert message={t("groups.loadError")} />
      )}
      {emptyState === "no-groups" && (
        <EmptyCard
          icon={<UsersIcon aria-hidden className="size-5" />}
          title={t("groups.empty")}
          hint={t("groups.emptyHint")}
        >
          <Button asChild size="sm">
            <Link to="/groups" search={{ ...search, create: true }}>
              <PlusIcon aria-hidden />
              {t("groups.create")}
            </Link>
          </Button>
        </EmptyCard>
      )}
      {emptyState === "no-results" && (
        <EmptyCard
          icon={<SearchIcon aria-hidden className="size-5" />}
          title={t("groups.noResults")}
          hint={t("groups.noResultsHint")}
        >
          <Button variant="outline" size="sm" onClick={resetFilters}>
            {t("groups.resetFilters")}
          </Button>
        </EmptyCard>
      )}
      {groups.data !== undefined && groups.data.groups.length > 0 && (
        <div className="overflow-hidden rounded-2xl border bg-card shadow-sm">
          <Table className="hidden md:table">
            <TableHeader className="bg-accent">
              <TableRow className="hover:bg-transparent">
                <TableHead>{t("groups.column.name")}</TableHead>
                <TableHead>{t("groups.column.schedule")}</TableHead>
                <TableHead>{t("groups.column.coaches")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {groups.data.groups.map((group) => (
                <TableRow key={group.id} className="relative align-top">
                  <TableCell className="whitespace-normal">
                    <GroupName group={group} />
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    <GroupSchedule schedule={group.schedule} />
                  </TableCell>
                  <TableCell className="whitespace-normal text-muted-foreground">
                    {group.employees.length === 0
                      ? "—"
                      : group.employees.map((e) => e.name).join(", ")}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <ul aria-label={t("groups.title")} className="divide-y md:hidden">
            {groups.data.groups.map((group) => (
              <li key={group.id} className="relative space-y-1 px-3.5 py-3 hover:bg-muted/50">
                <GroupName group={group} />
                <div className="text-sm">
                  <GroupSchedule schedule={group.schedule} />
                </div>
                {group.employees.length > 0 && (
                  <p className="text-sm text-muted-foreground">
                    {group.employees.map((e) => e.name).join(", ")}
                  </p>
                )}
              </li>
            ))}
          </ul>
        </div>
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
      <GroupCreateSheet
        api={api}
        open={search.create === true}
        onOpenChange={(open) => {
          onSearchChange({ ...searchOf(filters), ...(open ? { create: true } : {}) });
        }}
      />
    </section>
  );
}

/** Название группы [group] — растянутая на всю строку ссылка на карточку — и пометка о смене расписания. */
function GroupName({ group }: { readonly group: GroupListItem }) {
  const { t, format } = useI18n();
  return (
    <>
      <Link
        to="/groups/$groupId"
        params={{ groupId: group.id }}
        className="font-medium after:absolute after:inset-0 hover:underline"
      >
        {group.name}
      </Link>
      {group.scheduleChangeAt !== null && (
        <p className="text-xs text-amber-700 dark:text-amber-300">
          {t("groups.scheduleChangeNote", { date: format.numericDate(group.scheduleChangeAt) })}
        </p>
      )}
    </>
  );
}

/** Расписание [schedule] строками «дни время · зал»; без слотов — «Расписание не задано». */
function GroupSchedule({ schedule }: { readonly schedule: readonly ScheduleSlot[] }) {
  const { t, format } = useI18n();
  const rows = mergedScheduleRows(schedule);
  if (rows.length === 0) {
    return <span className="text-muted-foreground">{t("groups.noSchedule")}</span>;
  }
  return (
    <ul>
      {rows.map((row) => (
        <li key={`${row.startAt}-${row.endAt}-${row.days.join()}-${row.hallName ?? ""}`}>
          {row.days.map((day) => t(`day.${day}`)).join(", ")}{" "}
          <span className="tabular-nums">
            {format.time(row.startAt)}–{format.time(row.endAt)}
          </span>
          {row.hallName !== null && (
            <span className="text-muted-foreground"> · {row.hallName}</span>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Бейдж числа выбранных [count]; при нуле не рисуется. */
function CountBadge({ count }: { readonly count: number }) {
  if (count === 0) {
    return null;
  }
  return (
    <span className="rounded-full bg-primary px-2 text-xs text-primary-foreground tabular-nums">
      {count}
    </span>
  );
}

/** Чип выбранного значения фильтра: подпись [prefix], имя [name] и крестик [onRemove]. */
function FilterChip({
  prefix,
  name,
  onRemove,
}: {
  readonly prefix: string;
  readonly name: string;
  readonly onRemove: () => void;
}) {
  const { t } = useI18n();
  return (
    <span className="inline-flex items-center gap-1 rounded-full border bg-accent py-1 pr-1 pl-3 text-xs font-medium">
      <span className="text-muted-foreground">{prefix}</span>
      {name}
      <button
        type="button"
        onClick={onRemove}
        aria-label={t("groups.chip.remove", { name })}
        className="inline-flex size-5 items-center justify-center rounded-full hover:bg-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      >
        <XIcon aria-hidden className="size-3" />
      </button>
    </span>
  );
}

/** Карточка пустого списка: значок [icon], заголовок [title], пояснение [hint] и действие [children]. */
function EmptyCard({
  icon,
  title,
  hint,
  children,
}: {
  readonly icon: ReactNode;
  readonly title: string;
  readonly hint: string;
  readonly children: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-2xl border bg-card px-4 py-12 text-center shadow-sm">
      <span className="mb-1 inline-flex size-11 items-center justify-center rounded-full bg-accent text-muted-foreground">
        {icon}
      </span>
      <h2 className="text-base font-semibold">{title}</h2>
      <p className="mb-2 max-w-[40ch] text-sm text-muted-foreground">{hint}</p>
      {children}
    </div>
  );
}
