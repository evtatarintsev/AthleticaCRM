import { useQueryClient, useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { RowSelectionState } from "@tanstack/react-table";
import { Columns3Icon, PlusIcon, SearchIcon, SlidersHorizontalIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
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
  const filtered = filters.q !== "" || filterCount > 0;

  return (
    <section className="pb-24">
      <PageHeader
        title={t("clients.title")}
        actions={
          <>
            <Button
              variant="outline"
              onClick={() => {
                setColumnsOpen(true);
              }}
            >
              <Columns3Icon aria-hidden />
              {t("clients.columns")}
            </Button>
            <Button asChild>
              <Link to="/clients/new">
                <PlusIcon aria-hidden />
                {t("clients.create")}
              </Link>
            </Button>
          </>
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
              placeholder={t("clients.search")}
              aria-label={t("clients.search")}
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
            {t("clients.filters")}
            {filterCount > 0 && (
              <span className="rounded-full bg-primary text-primary-foreground px-2 text-xs tabular-nums">
                {filterCount}
              </span>
            )}
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-muted-foreground">
            {t("clients.count", { count: total })}
          </span>
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
                  ? "rounded-full border border-primary bg-primary px-3 py-1 text-xs font-medium text-primary-foreground"
                  : "rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
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
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}
      {pages.isError && <FormAlert message={t("clients.loadError")} />}
      {pages.data !== undefined && clients.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">
          {t(filtered ? "clients.noResults" : "clients.empty")}
        </p>
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
