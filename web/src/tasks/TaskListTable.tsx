import { Link } from "@tanstack/react-router";
import {
  createColumnHelper,
  rowSelectionFeature,
  tableFeatures,
  useTable,
  type RowSelectionState,
} from "@tanstack/react-table";
import type { Dispatch, SetStateAction } from "react";
import type { TaskListItemSchema } from "@/api/generated/contracts";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useI18n } from "@/i18n/context";
import { TaskStatusBadge } from "./TaskStatusBadge";
import type { TaskSortColumn } from "./taskListSearch";

/** Возможности таблицы: выбор строк. Сортировка — на клиенте, отдельным состоянием. */
const features = tableFeatures({ rowSelectionFeature });

const helper = createColumnHelper<typeof features, TaskListItemSchema>();
const columns = helper.columns([helper.accessor("title", {})]);

/** Сортировка таблицы задач: колонка и направление. */
export interface TaskSortState {
  readonly column: TaskSortColumn;
  readonly dir: "asc" | "desc";
}

/** Свойства таблицы и карточек списка задач. */
export interface TaskListTableProps {
  /** Задачи, отсортированные вызывающей стороной. */
  readonly tasks: readonly TaskListItemSchema[];
  /** Выбранные строки в терминах TanStack Table. */
  readonly rowSelection: RowSelectionState;
  /** Смена выбранных строк. */
  readonly onRowSelectionChange: Dispatch<SetStateAction<RowSelectionState>>;
  /** Текущая сортировка. */
  readonly sort: TaskSortState;
  /** Циклически меняет сортировку по колонке [column]. */
  readonly onSort: (column: TaskSortColumn) => void;
}

/**
 * Список задач: таблица на широком экране (≥ 768 px) и карточки на узком.
 * Клик по строке и карточке открывает карточку задачи, чекбоксы выбирают строки.
 */
export function TaskListTable(props: TaskListTableProps) {
  const { t, format } = useI18n();
  const table = useTable({
    features,
    columns,
    data: props.tasks,
    getRowId: (task) => task.id,
    state: { rowSelection: props.rowSelection },
    onRowSelectionChange: props.onRowSelectionChange,
  });
  const rows = table.getRowModel().rows;
  const allSelected = rows.length > 0 && rows.every((row) => row.getIsSelected());

  return (
    <>
      <div className="hidden overflow-x-auto rounded-md border md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={() => {
                    table.toggleAllRowsSelected(!allSelected);
                  }}
                  aria-label={t("directory.selectAll")}
                  className="size-4 accent-primary"
                />
              </TableHead>
              <SortHead
                label={t("tasks.column.title")}
                column="title"
                sort={props.sort}
                onSort={props.onSort}
              />
              <SortHead
                label={t("tasks.column.status")}
                column="status"
                sort={props.sort}
                onSort={props.onSort}
              />
              <SortHead
                label={t("tasks.column.assignee")}
                column="assignee"
                sort={props.sort}
                onSort={props.onSort}
              />
              <SortHead
                label={t("tasks.column.dueDate")}
                column="dueDate"
                sort={props.sort}
                onSort={props.onSort}
              />
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} className="relative">
                <TableCell className="w-10">
                  <input
                    type="checkbox"
                    checked={row.getIsSelected()}
                    onChange={row.getToggleSelectedHandler()}
                    aria-label={t("directory.select", { name: row.original.title })}
                    className="relative z-10 size-4 accent-primary"
                  />
                </TableCell>
                <TableCell>
                  <Link
                    to="/tasks/$taskId"
                    params={{ taskId: row.original.id }}
                    className="font-medium after:absolute after:inset-0 hover:underline"
                  >
                    {row.original.title}
                  </Link>
                </TableCell>
                <TableCell>
                  <TaskStatusBadge status={row.original.status} />
                </TableCell>
                <TableCell>{row.original.assigneeName ?? "—"}</TableCell>
                <TableCell>
                  {row.original.dueDate === null ? "—" : format.dateTime(row.original.dueDate)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <ul className="divide-y rounded-md border md:hidden">
        {rows.map((row) => (
          <li key={row.id} className="relative flex items-center gap-3 px-4 py-3">
            <input
              type="checkbox"
              checked={row.getIsSelected()}
              onChange={row.getToggleSelectedHandler()}
              aria-label={t("directory.select", { name: row.original.title })}
              className="relative z-10 size-4 shrink-0 accent-primary"
            />
            <Link
              to="/tasks/$taskId"
              params={{ taskId: row.original.id }}
              className="min-w-0 flex-1 after:absolute after:inset-0"
            >
              <span className="block truncate font-medium">{row.original.title}</span>
              <span className="block truncate text-sm text-muted-foreground">
                {[row.original.assigneeName, row.original.clientName]
                  .filter((value): value is string => value !== null)
                  .join(" · ")}
              </span>
              {row.original.dueDate !== null && (
                <span className="block truncate text-sm text-muted-foreground">
                  {format.dateTime(row.original.dueDate)}
                </span>
              )}
            </Link>
            <TaskStatusBadge status={row.original.status} />
          </li>
        ))}
      </ul>
    </>
  );
}

/** Заголовок с кнопкой сортировки по колонке [column] и текущим состоянием [sort]. */
function SortHead({
  label,
  column,
  sort,
  onSort,
}: {
  readonly label: string;
  readonly column: TaskSortColumn;
  readonly sort: TaskSortState;
  readonly onSort: (column: TaskSortColumn) => void;
}) {
  const { t } = useI18n();
  const active = sort.column === column;
  return (
    <TableHead aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}>
      <button
        type="button"
        onClick={() => {
          onSort(column);
        }}
        className="inline-flex items-center gap-1 whitespace-nowrap text-inherit outline-none hover:text-foreground focus-visible:underline"
      >
        {label}
        {active && <span aria-hidden>{sort.dir === "asc" ? "↑" : "↓"}</span>}
        <span className="sr-only">{t("tasks.sortBy", { name: label })}</span>
      </button>
    </TableHead>
  );
}
