import { Link } from "@tanstack/react-router";
import {
  createColumnHelper,
  rowSelectionFeature,
  tableFeatures,
  useTable,
  type RowSelectionState,
} from "@tanstack/react-table";
import { PencilIcon } from "lucide-react";
import type { Dispatch, SetStateAction } from "react";
import type { ApiClient } from "@/api/client";
import type { ClientListItem, ContactType } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import {
  clientColumnKey,
  standardColumnLabelKey,
  type ClientColumn,
  type ClientFieldKey,
} from "./clientColumns";
import { clientFieldValue, contactValues, customFieldDisplay, sortableField } from "./clientCells";
import type { ClientSortColumn } from "./clientListSearch";

/**
 * Возможности таблицы: выбор строк. Фильтрация и сортировка — на сервере,
 * функция фильтрации TanStack Table не подключается (см. `web/README.md`).
 */
const features = tableFeatures({ rowSelectionFeature });

const helper = createColumnHelper<typeof features, ClientListItem>();
const columns = helper.columns([helper.accessor("name", {})]);

/** Сортировка таблицы клиентов: колонка и направление. */
export interface ClientSortState {
  readonly column: ClientSortColumn;
  readonly dir: "asc" | "desc";
}

/** Свойства таблицы и карточек списка клиентов. */
export interface ClientListTableProps {
  /** Клиент API: по `avatarId` запрашиваются подписанные ссылки на аватары. */
  readonly api: ApiClient;
  /** Клиенты всех загруженных страниц. */
  readonly clients: readonly ClientListItem[];
  /** Колонки из настроек отображения; колонка имени добавляется сама. */
  readonly columns: readonly ClientColumn[];
  /** Выбранные строки в терминах TanStack Table. */
  readonly rowSelection: RowSelectionState;
  /** Смена выбранных строк. */
  readonly onRowSelectionChange: Dispatch<SetStateAction<RowSelectionState>>;
  /** Текущая сортировка. */
  readonly sort: ClientSortState;
  /** Циклически меняет сортировку по колонке [column]: возрастание → убывание → имя по возрастанию. */
  readonly onSort: (column: ClientSortColumn) => void;
}

/**
 * Список клиентов: таблица на широком экране (≥ 768 px) и карточки на узком.
 * Клик по строке и карточке открывает карточку клиента, чекбоксы выбирают строки.
 */
export function ClientListTable(props: ClientListTableProps) {
  const { t, format } = useI18n();
  const table = useTable({
    features,
    columns,
    data: props.clients,
    getRowId: (client) => client.id,
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
                label={t("clients.column.name")}
                column="name"
                sort={props.sort}
                onSort={props.onSort}
              />
              {props.columns.map((column) => (
                <ColumnHead
                  key={clientColumnKey(column)}
                  column={column}
                  sort={props.sort}
                  onSort={props.onSort}
                />
              ))}
              <TableHead className="w-10" />
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
                    aria-label={t("directory.select", { name: row.original.name })}
                    className="relative z-10 size-4 accent-primary"
                  />
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Avatar
                      api={props.api}
                      uploadId={row.original.avatarId}
                      name={row.original.name}
                    />
                    <Link
                      to="/clients/$clientId"
                      params={{ clientId: row.original.id }}
                      className="font-medium after:absolute after:inset-0 hover:underline"
                    >
                      {row.original.name}
                    </Link>
                  </div>
                </TableCell>
                {props.columns.map((column) => (
                  <TableCell key={clientColumnKey(column)}>
                    <ColumnCell column={column} client={row.original} />
                  </TableCell>
                ))}
                <TableCell className="w-10">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    asChild
                    className="relative z-10"
                    aria-label={t("clients.editClient", { name: row.original.name })}
                  >
                    <Link to="/clients/$clientId/edit" params={{ clientId: row.original.id }}>
                      <PencilIcon aria-hidden />
                    </Link>
                  </Button>
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
              aria-label={t("directory.select", { name: row.original.name })}
              className="relative z-10 size-4 shrink-0 accent-primary"
            />
            <Link
              to="/clients/$clientId"
              params={{ clientId: row.original.id }}
              aria-label={row.original.name}
              className="after:absolute after:inset-0"
            >
              <Avatar
                api={props.api}
                uploadId={row.original.avatarId}
                name={row.original.name}
                className="size-9"
              />
            </Link>
            <Link
              to="/clients/$clientId"
              params={{ clientId: row.original.id }}
              className="min-w-0 flex-1 after:absolute after:inset-0"
            >
              <span className="block truncate font-medium">{row.original.name}</span>
              {row.original.groups.length > 0 && (
                <span className="block truncate text-sm text-muted-foreground">
                  {row.original.groups.map((group) => group.name).join(", ")}
                </span>
              )}
            </Link>
            <span
              className={cn(
                "text-sm",
                row.original.balance.minorUnits < 0 ? "text-destructive" : undefined,
              )}
            >
              {format.money(row.original.balance)}
            </span>
            <Button
              variant="ghost"
              size="icon-sm"
              asChild
              className="relative z-10 shrink-0"
              aria-label={t("clients.editClient", { name: row.original.name })}
            >
              <Link to="/clients/$clientId/edit" params={{ clientId: row.original.id }}>
                <PencilIcon aria-hidden />
              </Link>
            </Button>
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
  readonly column: ClientSortColumn;
  readonly sort: ClientSortState;
  readonly onSort: (column: ClientSortColumn) => void;
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
        <span className="sr-only">{t("clients.sortBy", { name: label })}</span>
      </button>
    </TableHead>
  );
}

/** Заголовок колонки [column] настроек отображения; сортируемые поля — кнопкой сортировки. */
function ColumnHead({
  column,
  sort,
  onSort,
}: {
  readonly column: ClientColumn;
  readonly sort: ClientSortState;
  readonly onSort: (column: ClientSortColumn) => void;
}) {
  const { t } = useI18n();
  switch (column.kind) {
    case "standard": {
      const sortable = sortableField(column.field);
      return sortable === null ? (
        <TableHead className="text-center">{t(standardColumnLabelKey(column.field))}</TableHead>
      ) : (
        <SortHead
          label={t(standardColumnLabelKey(column.field))}
          column={sortable}
          sort={sort}
          onSort={onSort}
        />
      );
    }
    case "contact":
      return <TableHead className="text-center">{t(`contactType.${column.type}`)}</TableHead>;
    case "custom":
      return <TableHead className="text-center">{column.label}</TableHead>;
  }
}

/** Ячейка колонки [column] со значением клиента [client]. */
function ColumnCell({
  column,
  client,
}: {
  readonly column: ClientColumn;
  readonly client: ClientListItem;
}) {
  switch (column.kind) {
    case "standard":
      return <StandardColumnCell field={column.field} client={client} />;
    case "contact": {
      const values = contactValues(client, column.type);
      if (values.length === 0) {
        return <div className="text-center">—</div>;
      }
      return (
        <div className="relative z-10 text-center">
          {values.map((value, index) => (
            <span key={`${column.type}:${value}`}>
              {index > 0 && ", "}
              <ContactLink type={column.type} value={value} />
            </span>
          ))}
        </div>
      );
    }
    case "custom": {
      const value = clientFieldValue(client, column.fieldKey);
      return (
        <div className="text-center">{value === undefined ? "—" : customFieldDisplay(value)}</div>
      );
    }
  }
}

/** Ячейка стандартного поля [field] клиента [client]. */
function StandardColumnCell({
  field,
  client,
}: {
  readonly field: ClientFieldKey;
  readonly client: ClientListItem;
}) {
  const { format } = useI18n();
  switch (field) {
    case "gender":
      return (
        <div className="text-center">
          <GenderCell client={client} />
        </div>
      );
    case "birthday":
      return (
        <div className="text-center">
          {client.birthday === null ? "—" : format.date(client.birthday)}
        </div>
      );
    case "balance":
      return <BalanceCell client={client} />;
    case "groups": {
      const names = client.groups.map((group) => group.name).join(", ");
      return <div className="max-w-48 truncate">{names === "" ? "—" : names}</div>;
    }
  }
}

/** Пол клиента, локализованный. */
function GenderCell({ client }: { readonly client: ClientListItem }) {
  const { t } = useI18n();
  return t(`gender.${client.gender}`);
}

/** Баланс клиента с подсветкой отрицательного. */
function BalanceCell({ client }: { readonly client: ClientListItem }) {
  const { format } = useI18n();
  return (
    <div
      className={cn(
        "text-right tabular-nums",
        client.balance.minorUnits < 0 ? "text-destructive" : undefined,
      )}
    >
      {format.money(client.balance)}
    </div>
  );
}

/** Значение контакта [value] типа [type]: телефон и email — кликабельные ссылки. */
function ContactLink({ type, value }: { readonly type: ContactType; readonly value: string }) {
  if (type === "PHONE") {
    return (
      <a href={`tel:${value}`} className="text-primary hover:underline">
        {value}
      </a>
    );
  }
  if (type === "EMAIL") {
    return (
      <a href={`mailto:${value}`} className="text-primary hover:underline">
        {value}
      </a>
    );
  }
  return <span>{value}</span>;
}
