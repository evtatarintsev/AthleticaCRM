import { useQueryClient, useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { RowSelectionState } from "@tanstack/react-table";
import {
  CheckIcon,
  Columns3Icon,
  PlusIcon,
  SearchIcon,
  SlidersHorizontalIcon,
  UsersIcon,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { SelectionBar } from "@/ui/SelectionBar";
import { resolveClientColumns } from "./clientColumns";
import { ClientColumnsDialog } from "./ClientColumnsDialog";
import { ClientExportDialog } from "./ClientExportDialog";
import {
  activeFilterCount,
  CLIENT_VIEW_IDS,
  clearedFilters,
  clientListEmptyState,
  clientListFilters,
  searchOf,
  viewOf,
  type ClientListFilters,
  type ClientListSearch,
  type ClientSortColumn,
  type ClientViewId,
} from "./clientListSearch";
import { clientCustomFieldsQuery, displaySettingsQuery, useClientPages } from "./clientsQueries";
import { ClientListTable } from "./ClientListTable";
import { selectedClientIds } from "./clientCells";
import { ClientsFiltersSheet } from "./ClientsFiltersSheet";

/** Задержка между вводом в поиск и запросом к серверу, мс. */
const SEARCH_DEBOUNCE_MS = 400;

/**
 * Страница списка клиентов: поиск, системные виды, фильтры и сортировка в адресе
 * страницы, таблица с настраиваемыми колонками и карточки на узком экране,
 * выбор строк и групповые действия. Фильтры переживают перезагрузку.
 */
export function ClientsPage({
  api,
  search,
  onSearchChange,
}: {
  /** Клиент API. */
  readonly api: ApiClient;
  /** Search-параметры адреса: фильтры и сортировка. */
  readonly search: ClientListSearch;
  /** Заменяет search-параметры адреса. */
  readonly onSearchChange: (search: ClientListSearch) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const filters = useMemo(() => clientListFilters(search), [search]);
  const pages = useClientPages(api, branchId, filters);
  const customFields = useQuery(clientCustomFieldsQuery(api, branchId));
  const displaySettings = useQuery(displaySettingsQuery(api));
  const columns = useMemo(
    () =>
      resolveClientColumns(displaySettings.data?.clients.columns ?? [], customFields.data ?? []),
    [displaySettings.data, customFields.data],
  );
  const clients = useMemo(
    () => (pages.data?.pages ?? []).flatMap((page) => page.clients),
    [pages.data],
  );
  const total = pages.data?.pages.at(-1)?.total ?? 0;

  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [columnsOpen, setColumnsOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
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
    (next: ClientListFilters) => {
      onSearchChange(searchOf(next));
    },
    [onSearchChange],
  );

  const cycleSort = useCallback(
    (column: ClientSortColumn) => {
      if (filters.sort !== column) {
        setFilters({ ...filters, sort: column, dir: "asc" });
      } else if (filters.dir === "asc") {
        setFilters({ ...filters, dir: "desc" });
      } else {
        setFilters({ ...filters, sort: "name", dir: "asc" });
      }
    },
    [filters, setFilters],
  );

  const applyView = useCallback(
    (view: ClientViewId) => {
      const cleared = {
        gender: "all" as const,
        debt: false,
        noGroup: false,
        birthday: "none" as const,
        archived: false,
      };
      const presets: Readonly<Record<ClientViewId, typeof cleared>> = {
        all: cleared,
        debt: { ...cleared, debt: true },
        "no-group": { ...cleared, noGroup: true },
        archived: { ...cleared, archived: true },
      };
      setFilters({ ...filters, ...presets[view] });
    },
    [filters, setFilters],
  );

  const selectedIds = selectedClientIds(clients, rowSelection);
  const bulk = useCallback(
    async (action: "archive" | "restore") => {
      const result = await api.call(action === "archive" ? "clients/archive" : "clients/restore", {
        clientIds: selectedIds,
      });
      if (!result.ok) {
        toast.error(apiErrorMessage(t, result.error));
        return;
      }
      setRowSelection({});
      await queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] });
      toast.success(t(action === "archive" ? "clients.archivedToast" : "clients.restoredToast"));
    },
    [api, branchId, queryClient, selectedIds, setRowSelection, t],
  );

  const view = viewOf(filters);
  const filterCount = activeFilterCount(filters);

  return (
    <section className="pb-24">
      <PageHeader
        title={t("clients.title")}
        description={pages.data === undefined ? undefined : t("clients.count", { count: total })}
        actions={
          <div className="flex gap-2">
            <Button
              variant="outline"
              className="hidden md:inline-flex"
              onClick={() => {
                setColumnsOpen(true);
              }}
            >
              <Columns3Icon aria-hidden />
              {t("clients.columns")}
            </Button>
            <Button asChild className="max-md:w-9 max-md:has-[>svg]:px-0">
              <Link to="/clients/new">
                <PlusIcon aria-hidden />
                <span className="sr-only md:not-sr-only">{t("clients.create")}</span>
              </Link>
            </Button>
          </div>
        }
      />

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
              placeholder={t("clients.search")}
              aria-label={t("clients.search")}
              className="pl-9"
            />
          </div>
          <Button
            variant="outline"
            className="max-md:has-[>svg]:px-2.5"
            onClick={() => {
              setFiltersOpen(true);
            }}
          >
            <SlidersHorizontalIcon aria-hidden />
            <span className="sr-only md:not-sr-only">{t("clients.filters")}</span>
            {filterCount > 0 && (
              <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
                {filterCount}
              </span>
            )}
          </Button>
        </div>

        <div
          role="group"
          aria-label={t("clients.viewsLabel")}
          className="sticky top-14 z-30 -mx-4 flex gap-2 overflow-x-auto bg-background px-4 py-2 [scrollbar-width:none] md:static md:mx-0 md:flex-wrap md:p-0"
        >
          {CLIENT_VIEW_IDS.map((id) => (
            <button
              key={id}
              type="button"
              aria-pressed={view === id}
              onClick={() => {
                applyView(id);
              }}
              className={
                view === id
                  ? "shrink-0 rounded-full border border-primary bg-primary px-3 py-1 text-xs font-medium text-primary-foreground"
                  : "shrink-0 rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
              }
            >
              {t(viewLabelKey(id))}
            </button>
          ))}
        </div>

        {(filters.gender !== "all" ||
          filters.debt ||
          filters.noGroup ||
          filters.birthday !== "none") && (
          <div className="flex flex-wrap items-center gap-2">
            {filters.gender !== "all" && (
              <FilterChip
                label={t(
                  filters.gender === "male"
                    ? "clients.filter.genderMale"
                    : "clients.filter.genderFemale",
                )}
                onRemove={() => {
                  setFilters({ ...filters, gender: "all" });
                }}
              />
            )}
            {filters.debt && (
              <FilterChip
                label={t("clients.filter.debt")}
                onRemove={() => {
                  setFilters({ ...filters, debt: false });
                }}
              />
            )}
            {filters.noGroup && (
              <FilterChip
                label={t("clients.filter.noGroup")}
                onRemove={() => {
                  setFilters({ ...filters, noGroup: false });
                }}
              />
            )}
            {filters.birthday !== "none" && (
              <FilterChip
                label={t(
                  filters.birthday === "today"
                    ? "clients.filter.chipBirthdayToday"
                    : filters.birthday === "tomorrow"
                      ? "clients.filter.chipBirthdayTomorrow"
                      : filters.birthday === "todayTomorrow"
                        ? "clients.filter.chipBirthdayTodayTomorrow"
                        : filters.birthday === "weekAhead"
                          ? "clients.filter.chipBirthdayWeekAhead"
                          : "clients.filter.chipBirthdayWeek",
                )}
                onRemove={() => {
                  setFilters({ ...filters, birthday: "none" });
                }}
              />
            )}
          </div>
        )}
      </div>

      {pages.isPending && (
        <div className="space-y-3 rounded-xl border bg-card p-4 shadow-xs">
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
      )}
      {pages.isError && <FormAlert message={t("clients.loadError")} />}
      {pages.data !== undefined && clients.length === 0 && (
        <ClientListEmpty
          filters={filters}
          onReset={() => {
            setFilters(clearedFilters(filters));
          }}
        />
      )}
      {clients.length > 0 && (
        <ClientListTable
          api={api}
          clients={clients}
          columns={columns}
          rowSelection={rowSelection}
          onRowSelectionChange={setRowSelection}
          sort={{ column: filters.sort, dir: filters.dir }}
          onSort={cycleSort}
        />
      )}
      {pages.data !== undefined && pages.hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="outline"
            disabled={pages.isFetchingNextPage}
            onClick={() => {
              void pages.fetchNextPage();
            }}
          >
            {t("clients.loadMore")}
          </Button>
        </div>
      )}

      <SelectionBar
        count={selectedIds.length}
        actions={
          <>
            <Button
              variant="outline"
              onClick={() => {
                setExportOpen(true);
              }}
            >
              {t("clients.export.action")}
            </Button>
            {filters.archived ? (
              <Button
                variant="outline"
                onClick={() => {
                  void bulk("restore");
                }}
              >
                {t("clients.restoreSelected")}
              </Button>
            ) : (
              <Button
                variant="outline"
                onClick={() => {
                  void bulk("archive");
                }}
              >
                {t("clients.archiveSelected")}
              </Button>
            )}
          </>
        }
      />

      <ClientsFiltersSheet
        open={filtersOpen}
        filters={filters}
        onOpenChange={setFiltersOpen}
        onApply={setFilters}
      />
      <ClientColumnsDialog
        api={api}
        open={columnsOpen}
        columns={columns}
        customFields={customFields.data ?? []}
        onOpenChange={setColumnsOpen}
      />
      <ClientExportDialog
        api={api}
        customFields={customFields.data ?? []}
        open={exportOpen}
        onOpenChange={setExportOpen}
      />
    </section>
  );
}

/**
 * Пустой список по фильтрам [filters]: приглашение создать первого клиента, пустой
 * системный вид или «ничего не найдено» с действием [onReset].
 */
function ClientListEmpty({
  filters,
  onReset,
}: {
  readonly filters: ClientListFilters;
  readonly onReset: () => void;
}) {
  const { t } = useI18n();
  const view = viewOf(filters);
  switch (clientListEmptyState(filters)) {
    case "no-clients":
      return (
        <EmptyCard icon={<UsersIcon aria-hidden className="size-5" />} text={t("clients.empty")}>
          <Button asChild size="sm">
            <Link to="/clients/new">
              <PlusIcon aria-hidden />
              {t("clients.create")}
            </Link>
          </Button>
        </EmptyCard>
      );
    case "view-empty":
      return (
        <EmptyCard
          icon={<CheckIcon aria-hidden className="size-5" />}
          iconClassName="bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400"
          text={view === null || view === "all" ? "" : t(emptyViewKey(view))}
        />
      );
    case "no-results":
      return (
        <EmptyCard
          icon={<SearchIcon aria-hidden className="size-5" />}
          text={t("clients.noResults")}
        >
          <Button variant="outline" size="sm" onClick={onReset}>
            {t("clients.resetFilters")}
          </Button>
        </EmptyCard>
      );
  }
}

/** Карточка пустого списка: значок [icon] в круге [iconClassName], текст [text] и действие [children]. */
function EmptyCard({
  icon,
  iconClassName = "bg-muted",
  text,
  children,
}: {
  readonly icon: ReactNode;
  readonly iconClassName?: string;
  readonly text: string;
  readonly children?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2.5 rounded-xl border bg-card px-4 py-12 text-center text-sm text-muted-foreground shadow-xs">
      <span
        className={cn(
          "inline-flex size-11 items-center justify-center rounded-full",
          iconClassName,
        )}
      >
        {icon}
      </span>
      {text}
      {children}
    </div>
  );
}

/** Ключ текста пустого системного вида [view] в словаре. */
function emptyViewKey(view: Exclude<ClientViewId, "all">): PlainMessageKey {
  switch (view) {
    case "debt":
      return "clients.emptyView.debt";
    case "no-group":
      return "clients.emptyView.noGroup";
    case "archived":
      return "clients.emptyView.archived";
  }
}

/** Ключ подписи системного вида [view] в словаре. */
function viewLabelKey(view: ClientViewId): PlainMessageKey {
  switch (view) {
    case "all":
      return "clients.view.all";
    case "debt":
      return "clients.view.debt";
    case "no-group":
      return "clients.view.noGroup";
    case "archived":
      return "clients.view.archived";
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
