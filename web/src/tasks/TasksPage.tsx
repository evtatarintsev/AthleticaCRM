import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { RowSelectionState } from "@tanstack/react-table";
import { PlusIcon, SearchIcon, SlidersHorizontalIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { EmployeeId, TaskListItemSchema, TaskStatus } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { SelectionBar } from "@/ui/SelectionBar";
import { AssigneeSheet } from "./AssigneeSheet";
import { TaskCreateSheet } from "./TaskCreateSheet";
import { TaskListTable, type TaskSortState } from "./TaskListTable";
import { TaskSheet } from "./TaskSheet";
import { taskStatusLabelKey } from "./taskStatus";
import {
  activeFilterCount,
  panelOf,
  searchOf,
  taskListFilters,
  TASK_VIEW_IDS,
  viewOf,
  withPanel,
  type TaskListFilters,
  type TaskListSearch,
  type TaskSortColumn,
  type TaskViewId,
} from "./taskListSearch";
import { tasksQuery } from "./tasksQueries";
import { TasksFiltersSheet } from "./TasksFiltersSheet";

/** Задержка между вводом в поиск и запросом к серверу, мс. */
const SEARCH_DEBOUNCE_MS = 400;

/** Статусы задач в порядке, используемом бейджами и меню. */
const ALL_STATUSES: readonly TaskStatus[] = ["PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED"];

/** Идентификаторы выбранных задач из состояния таблицы [rowSelection]. */
function selectedTaskIds(
  tasks: readonly TaskListItemSchema[],
  rowSelection: RowSelectionState,
): readonly TaskListItemSchema["id"][] {
  return tasks.filter((task) => rowSelection[task.id] === true).map((task) => task.id);
}

/** Сравнение задач [a] и [b] по колонке [column] для сортировки на клиенте. */
function compareTasks(
  a: TaskListItemSchema,
  b: TaskListItemSchema,
  column: TaskSortColumn,
): number {
  switch (column) {
    case "title":
      return a.title.localeCompare(b.title);
    case "status":
      return ALL_STATUSES.indexOf(a.status) - ALL_STATUSES.indexOf(b.status);
    case "assignee":
      return (a.assigneeName ?? "").localeCompare(b.assigneeName ?? "");
    case "dueDate": {
      const aTime = a.dueDate === null ? Number.POSITIVE_INFINITY : new Date(a.dueDate).getTime();
      const bTime = b.dueDate === null ? Number.POSITIVE_INFINITY : new Date(b.dueDate).getTime();
      return aTime - bTime;
    }
  }
}

/**
 * Страница списка задач: поиск, системные виды («Все»/«Мои»), фильтр по статусам,
 * сортировка на клиенте, таблица с множественным выбором и групповыми действиями.
 * Создание и карточка задачи открываются панелями поверх списка; открытая панель
 * хранится в адресе. Видимость задач по праву `CAN_VIEW_ALL_TASKS` целиком определяет сервер.
 */
export function TasksPage({
  api,
  search,
  onSearchChange,
  onPanelChange,
}: {
  readonly api: ApiClient;
  readonly search: TaskListSearch;
  /** Смена фильтров списка: новая запись в истории. */
  readonly onSearchChange: (search: TaskListSearch) => void;
  /** Открытие или закрытие панели: адрес заменяется, чтобы «Назад» не листал панели. */
  readonly onPanelChange: (search: TaskListSearch) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const filters = useMemo(() => taskListFilters(search), [search]);
  const tasksResult = useQuery(tasksQuery(api, branchId, filters));
  const panel = panelOf(search);

  const tasks = useMemo(() => {
    const list = tasksResult.data?.tasks ?? [];
    const sorted = [...list].sort((a, b) => compareTasks(a, b, filters.sort));
    return filters.dir === "asc" ? sorted : sorted.reverse();
  }, [tasksResult.data, filters.sort, filters.dir]);

  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [bulkAssignOpen, setBulkAssignOpen] = useState(false);
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
    (next: TaskListFilters) => {
      onSearchChange(searchOf(next));
    },
    [onSearchChange],
  );

  const cycleSort = useCallback(
    (column: TaskSortColumn) => {
      if (filters.sort !== column) {
        setFilters({ ...filters, sort: column, dir: "asc" });
      } else if (filters.dir === "asc") {
        setFilters({ ...filters, dir: "desc" });
      } else {
        setFilters({ ...filters, sort: "dueDate", dir: "asc" });
      }
    },
    [filters, setFilters],
  );

  const applyView = useCallback(
    (view: TaskViewId) => {
      setFilters({ ...filters, onlyMine: view === "mine", statuses: [] });
    },
    [filters, setFilters],
  );

  const selectedIds = selectedTaskIds(tasks, rowSelection);
  const invalidate = useCallback(
    () => queryClient.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"] }),
    [queryClient, branchId],
  );

  const changeStatus = useCallback(
    async (status: TaskStatus) => {
      const result = await api.call("tasks/status", { taskIds: selectedIds, status });
      if (!result.ok) {
        toast.error(apiErrorMessage(t, result.error));
        return;
      }
      setRowSelection({});
      await invalidate();
      toast.success(t("tasks.statusChangedToast"));
    },
    [api, invalidate, selectedIds, t],
  );

  const assign = useCallback(
    async (assigneeId: EmployeeId | null): Promise<string | null> => {
      const result =
        assigneeId === null
          ? await api.call("tasks/unassign", { taskIds: selectedIds })
          : await api.call("tasks/assign", { taskIds: selectedIds, assigneeId });
      if (!result.ok) {
        return apiErrorMessage(t, result.error);
      }
      setRowSelection({});
      await invalidate();
      toast.success(t("tasks.assigneeChangedToast"));
      return null;
    },
    [api, invalidate, selectedIds, t],
  );

  const view = viewOf(filters);
  const filterCount = activeFilterCount(filters);
  const filtered = filters.q !== "" || filterCount > 0;
  const sort: TaskSortState = { column: filters.sort, dir: filters.dir };

  return (
    <section className="pb-24">
      <PageHeader
        title={t("tasks.title")}
        actions={
          <Button asChild>
            <Link to="/tasks" search={withPanel(search, { kind: "create" })} replace>
              <PlusIcon aria-hidden />
              {t("tasks.create")}
            </Link>
          </Button>
        }
      />

      <div className="mb-4 space-y-3">
        <div className="flex flex-wrap items-center gap-2">
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
              placeholder={t("tasks.searchPlaceholder")}
              aria-label={t("tasks.searchPlaceholder")}
              className="pl-9"
            />
          </div>
          <Button
            variant="outline"
            onClick={() => {
              setFiltersOpen(true);
            }}
          >
            <SlidersHorizontalIcon aria-hidden />
            {t("tasks.filters")}
            {filterCount > 0 && (
              <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
                {filterCount}
              </span>
            )}
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-muted-foreground">
            {t("tasks.count", { count: tasks.length })}
          </span>
          {TASK_VIEW_IDS.map((id) => (
            <button
              key={id}
              type="button"
              aria-pressed={view === id}
              onClick={() => {
                applyView(id);
              }}
              className={
                view === id
                  ? "rounded-full border border-primary bg-primary px-3 py-1 text-xs font-medium text-primary-foreground"
                  : "rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
              }
            >
              {t(viewLabelKey(id))}
            </button>
          ))}
        </div>

        {filters.statuses.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            {filters.statuses.map((status) => (
              <FilterChip
                key={status}
                label={t(taskStatusLabelKey(status))}
                onRemove={() => {
                  setFilters({
                    ...filters,
                    statuses: filters.statuses.filter((s) => s !== status),
                  });
                }}
              />
            ))}
          </div>
        )}
      </div>

      {tasksResult.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}
      {tasksResult.isError && <FormAlert message={t("tasks.loadError")} />}
      {tasksResult.data !== undefined && tasks.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">
          {t(filtered ? "directory.noResults" : "tasks.empty")}
        </p>
      )}
      {tasks.length > 0 && (
        <TaskListTable
          tasks={tasks}
          rowSelection={rowSelection}
          onRowSelectionChange={setRowSelection}
          sort={sort}
          onSort={cycleSort}
          cardSearch={(taskId) => withPanel(search, { kind: "task", taskId })}
        />
      )}

      <SelectionBar
        count={selectedIds.length}
        actions={
          <>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">{t("tasks.bulkChangeStatus")}</Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {ALL_STATUSES.map((status) => (
                  <DropdownMenuItem
                    key={status}
                    onSelect={() => {
                      void changeStatus(status);
                    }}
                  >
                    {t(taskStatusLabelKey(status))}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
            <Button
              variant="outline"
              onClick={() => {
                setBulkAssignOpen(true);
              }}
            >
              {t("tasks.bulkAssign")}
            </Button>
          </>
        }
      />

      <TasksFiltersSheet
        open={filtersOpen}
        filters={filters}
        onOpenChange={setFiltersOpen}
        onApply={setFilters}
      />
      <AssigneeSheet
        api={api}
        open={bulkAssignOpen}
        onOpenChange={setBulkAssignOpen}
        onChoose={assign}
      />
      <TaskCreateSheet
        api={api}
        open={panel?.kind === "create"}
        onOpenChange={(open) => {
          onPanelChange(withPanel(search, open ? { kind: "create" } : null));
        }}
      />
      <TaskSheet
        api={api}
        taskId={panel?.kind === "task" ? panel.taskId : null}
        onClose={() => {
          onPanelChange(withPanel(search, null));
        }}
      />
    </section>
  );
}

/** Ключ подписи системного вида [view] в словаре. */
function viewLabelKey(view: TaskViewId): PlainMessageKey {
  switch (view) {
    case "all":
      return "tasks.view.all";
    case "mine":
      return "tasks.view.mine";
  }
}

/** Чип активного фильтра [label] с крестиком удаления. */
function FilterChip({
  label,
  onRemove,
}: {
  readonly label: string;
  readonly onRemove: () => void;
}) {
  const { t } = useI18n();
  return (
    <button
      type="button"
      onClick={onRemove}
      className="inline-flex items-center gap-1 rounded-full border bg-accent px-3 py-1 text-xs font-medium hover:bg-accent/70"
    >
      {label}
      <span aria-hidden>×</span>
      <span className="sr-only">{t("action.clear")}</span>
    </button>
  );
}
