import { useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import {
  createColumnHelper,
  rowSelectionFeature,
  tableFeatures,
  useTable,
  type RowSelectionState,
} from "@tanstack/react-table";
import { PlusIcon, SearchIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient, ApiResult, EndpointPath } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { markExisting, type SuggestionGroup } from "@/forms/nameSuggestions";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetBody, EditSheetSelection } from "@/ui/EditSheet";
import { DirectoryItemSheet } from "./DirectoryItemSheet";

/** Запись справочника: идентификатор, название и — у справочников с пояснением — пояснение. */
export interface DirectoryItem<Id extends string> {
  readonly id: Id;
  readonly name: string;
  /** Что относится к записи; есть только у справочников с [DirectoryDefinition.describable]. */
  readonly description?: string;
}

/** Справочник, который показывает [DirectorySheet]: подписи, загрузка и изменение записей. */
export interface DirectoryDefinition<Id extends string> {
  /** Заголовок панели справочника. */
  readonly title: PlainMessageKey;
  /** Заголовок панели создания записи. */
  readonly createTitle: PlainMessageKey;
  /** Заголовок панели редактирования записи. */
  readonly editTitle: PlainMessageKey;
  /** Эндпоинт списка: его кэш сбрасывается после изменений. */
  readonly listPath: EndpointPath;
  /** Записи справочника в филиале [branchId]. */
  readonly useItems: (
    api: ApiClient,
    branchId: BranchId,
  ) => UseQueryResult<readonly DirectoryItem<Id>[]>;
  /** Идентификатор новой записи. */
  readonly newId: () => Id;
  /** Создаёт запись [item]. */
  readonly create: (api: ApiClient, item: DirectoryItem<Id>) => Promise<ApiResult<undefined>>;
  /** Переименовывает запись [item]. */
  readonly update: (api: ApiClient, item: DirectoryItem<Id>) => Promise<ApiResult<undefined>>;
  /** Удаляет записи [ids]. */
  readonly remove: (api: ApiClient, ids: readonly Id[]) => Promise<ApiResult<undefined>>;
  /** Другие запросы, которые зависят от справочника и сбрасываются вместе со списком. */
  readonly alsoInvalidates?: readonly (readonly unknown[])[];
  /** У записей есть необязательное пояснение: оно показывается в списке и редактируется в панели записи. */
  readonly describable?: boolean;
  /**
   * Подсказки названия при создании записи в филиале [branchId]; `undefined`, пока они
   * загружаются или недоступны, — тогда поле названия обычное.
   */
  readonly useNameSuggestions?: (
    api: ApiClient,
    branchId: BranchId,
  ) => readonly SuggestionGroup[] | undefined;
}

/** Справочник без подсказок названия. */
function noNameSuggestions(): undefined {
  return undefined;
}

/**
 * Возможности таблицы: выбор строк. Поиск по названию делается до таблицы: функции фильтрации
 * TanStack Table принимают значение фильтра как `any`, а строгий `type-coverage` этого не пропускает.
 */
const features = tableFeatures({ rowSelectionFeature });

/** Колонки таблицы: одно название. */
const helper = createColumnHelper<typeof features, DirectoryItem<string>>();
const columns = helper.columns([helper.accessor("name", {})]);

/**
 * Что редактируется во вложенной панели: новая запись или существующая. Значение остаётся
 * после закрытия панели, чтобы её содержимое не пропадало во время анимации закрытия.
 */
type Editing<Id extends string> =
  { readonly kind: "create" } | { readonly kind: "edit"; readonly item: DirectoryItem<Id> };

/** Панель справочника [definition], открытая при [open]. */
export function DirectorySheet<Id extends string>({
  api,
  definition,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  definition: DirectoryDefinition<Id>;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t(definition.title)} size="lg">
      <DirectoryPanel api={api} definition={definition} />
    </EditSheet>
  );
}

/**
 * Содержимое панели справочника: список с поиском, выбор нескольких записей и их удаление
 * с подтверждением. Создание и переименование открываются панелью поверх списка.
 */
function DirectoryPanel<Id extends string>({
  api,
  definition,
}: {
  api: ApiClient;
  definition: DirectoryDefinition<Id>;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const items = definition.useItems(api, branchId);
  const useNameSuggestions = definition.useNameSuggestions ?? noNameSuggestions;
  const suggestionGroups = useNameSuggestions(api, branchId);
  const suggestions = useMemo(
    () =>
      suggestionGroups === undefined
        ? undefined
        : {
            groups: suggestionGroups,
            existing: markExisting(
              suggestionGroups,
              (items.data ?? []).map((item) => item.name),
            ),
          },
    [suggestionGroups, items.data],
  );
  const [editing, setEditing] = useState<Editing<Id>>({ kind: "create" });
  const [editorOpen, setEditorOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [search, setSearch] = useState("");

  const visible = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    const all: readonly DirectoryItem<Id>[] = items.data ?? [];
    return query === "" ? all : all.filter((item) => item.name.toLocaleLowerCase().includes(query));
  }, [items.data, search]);
  const table = useTable({
    features,
    columns,
    data: visible,
    getRowId: (item) => item.id,
    state: { rowSelection },
    onRowSelectionChange: setRowSelection,
  });
  const selected = (items.data ?? []).filter((item) => rowSelection[item.id] === true);
  const allVisibleSelected =
    visible.length > 0 && visible.every((item) => rowSelection[item.id] === true);

  const refresh = () =>
    Promise.all(
      [["api", branchId, definition.listPath], ...(definition.alsoInvalidates ?? [])].map(
        (queryKey) => queryClient.invalidateQueries({ queryKey }),
      ),
    );

  const save = async (item: DirectoryItem<Id>, isNew: boolean): Promise<string | null> => {
    const result = await (isNew ? definition.create(api, item) : definition.update(api, item));
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(t("directory.saved"));
    return null;
  };

  const removeSelected = async () => {
    const result = await definition.remove(
      api,
      selected.map((item) => item.id),
    );
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    setRowSelection({});
    await refresh();
    toast.success(t("directory.deleted"));
  };

  const toggleAllVisible = () => {
    const visibleSet = new Set<string>(visible.map((item) => item.id));
    setRowSelection((current) =>
      allVisibleSelected
        ? Object.fromEntries(Object.entries(current).filter(([id]) => !visibleSet.has(id)))
        : { ...current, ...Object.fromEntries(visible.map((item) => [item.id, true])) },
    );
  };

  const openEditor = (next: Editing<Id>) => {
    setEditing(next);
    setEditorOpen(true);
  };

  return (
    <>
      <EditSheetBody>
        <div className="flex items-center gap-2">
          {items.data !== undefined && items.data.length > 0 && (
            <div className="relative flex-1">
              <SearchIcon
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                type="search"
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                }}
                placeholder={t("directory.search")}
                aria-label={t("directory.search")}
                className="pl-9"
              />
            </div>
          )}
          <Button
            className="ml-auto"
            onClick={() => {
              openEditor({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("action.add")}
          </Button>
        </div>
        {items.isPending && (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        )}
        {items.isError && <FormAlert message={t("directory.loadError")} />}
        {items.data?.length === 0 && (
          <p className="py-12 text-center text-muted-foreground">{t("directory.empty")}</p>
        )}
        {items.data !== undefined &&
          items.data.length > 0 &&
          (visible.length === 0 ? (
            <p className="py-12 text-center text-muted-foreground">{t("directory.noResults")}</p>
          ) : (
            <div className="rounded-md border">
              <label className="flex items-center gap-3 border-b px-4 py-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  checked={allVisibleSelected}
                  onChange={toggleAllVisible}
                  className="size-4 accent-primary"
                />
                {t("directory.selectAll")}
              </label>
              <ul className="divide-y">
                {table.getRowModel().rows.map((row) => (
                  <li key={row.id} className="flex items-center gap-3 px-4">
                    <input
                      type="checkbox"
                      checked={row.getIsSelected()}
                      onChange={row.getToggleSelectedHandler()}
                      aria-label={t("directory.select", { name: row.original.name })}
                      className="size-4 shrink-0 accent-primary"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const item = visible.find((v) => v.id === row.id);
                        if (item !== undefined) {
                          openEditor({ kind: "edit", item });
                        }
                      }}
                      aria-label={t("directory.edit", { name: row.original.name })}
                      className="group min-w-0 flex-1 py-3 text-left outline-none"
                    >
                      <span className="block truncate font-medium group-hover:underline group-focus-visible:underline">
                        {row.original.name}
                      </span>
                      {definition.describable === true &&
                        row.original.description !== undefined &&
                        row.original.description !== "" && (
                          <span className="line-clamp-2 text-sm text-muted-foreground">
                            {row.original.description}
                          </span>
                        )}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
      </EditSheetBody>
      <EditSheetSelection
        count={selected.length}
        actions={
          <Button
            variant="destructive"
            onClick={() => {
              setConfirmDelete(true);
            }}
          >
            {t("directory.deleteSelected")}
          </Button>
        }
      />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t("directory.deleteTitle", { count: selected.length })}
        description={t("directory.deleteText")}
        confirmLabel={t("action.delete")}
        onConfirm={removeSelected}
      />
      <DirectoryItemSheet
        open={editorOpen}
        onOpenChange={setEditorOpen}
        title={t(editing.kind === "create" ? definition.createTitle : definition.editTitle)}
        describable={definition.describable === true}
        suggestions={editing.kind === "create" ? suggestions : undefined}
        initial={
          editing.kind === "create"
            ? { name: "", description: "" }
            : { name: editing.item.name, description: editing.item.description ?? "" }
        }
        onSave={({ name, description }) => {
          const fields = definition.describable === true ? { name, description } : { name };
          return editing.kind === "create"
            ? save({ id: definition.newId(), ...fields }, true)
            : save({ id: editing.item.id, ...fields }, false);
        }}
      />
    </>
  );
}
