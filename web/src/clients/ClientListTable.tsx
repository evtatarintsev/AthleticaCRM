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
import { ClientBalance } from "./ClientBalance";
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
  readonly column: ClientSortColumn | "nextBirthday";
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
  const { t } = useI18n();
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
      <div className="hidden overflow-x-auto rounded-xl border bg-card shadow-xs md:block">
        <Table>
          <TableHeader className="bg-muted/50 text-muted-foreground">
            <TableRow className="hover:bg-transparent">
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
              <TableRow
                key={row.id}
                data-state={row.getIsSelected() ? "selected" : undefined}
                className="relative data-[state=selected]:bg-primary/5"
              >
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
      <ul
        aria-label={t("clients.title")}
        className="divide-y overflow-hidden rounded-xl border bg-card shadow-xs md:hidden"
      >
        {rows.map((row) => (
          <li
            key={row.id}
            data-state={row.getIsSelected() ? "selected" : undefined}
            className="relative flex items-center gap-3 px-3.5 py-3 data-[state=selected]:bg-primary/5"
          >
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
            <span className="shrink-0 text-sm">
              <ClientBalance balance={row.original.balance} />
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

/**
 * Заголовок с кнопкой сортировки по колонке [column] и текущим состоянием [sort];
 * [className] — выравнивание заголовка по содержимому колонки.
 */
function SortHead({
  label,
  column,
  sort,
  onSort,
  className,
}: {
  readonly label: string;
  readonly className?: string | undefined;
  readonly column: ClientSortColumn;
  readonly sort: ClientSortState;
  readonly onSort: (column: ClientSortColumn) => void;
}) {
  const { t } = useI18n();
  const active = sort.column === column;
  return (
    <TableHead
      aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
      className={className}
    >
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
          className={column.field === "balance" ? "text-right" : undefined}
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
      return (
        <div className="text-right">
          <ClientBalance balance={client.balance} />
        </div>
      );
    case "groups":
      return <GroupChips client={client} />;
  }
}

/** Пол клиента, локализованный. */
function GenderCell({ client }: { readonly client: ClientListItem }) {
  const { t } = useI18n();
  return t(`gender.${client.gender}`);
}

/** Сколько групп клиента показывается чипами; остальные сворачиваются в «+N». */
const VISIBLE_GROUPS = 2;

/** Группы клиента [client] чипами-ссылками на страницу группы; лишние — чипом «+N» с подсказкой. */
function GroupChips({ client }: { readonly client: ClientListItem }) {
  const { t } = useI18n();
  if (client.groups.length === 0) {
    return <span className="text-muted-foreground">—</span>;
  }
  const hidden = client.groups.slice(VISIBLE_GROUPS);
  const chip =
    "relative z-10 inline-block h-6.5 max-w-36 truncate rounded-lg border bg-muted/50 px-2.5 text-xs leading-6 font-medium";
  return (
    <div className="flex gap-1.5">
      {client.groups.slice(0, VISIBLE_GROUPS).map((group) => (
        <Link
          key={group.id}
          to="/groups/$groupId"
          params={{ groupId: group.id }}
          className={cn(chip, "text-primary hover:border-foreground/20")}
        >
          {group.name}
        </Link>
      ))}
      {hidden.length > 0 && (
        <span
          title={hidden.map((group) => group.name).join(", ")}
          className={cn(chip, "text-muted-foreground")}
        >
          {t("clients.groupsMore", { count: hidden.length })}
        </span>
      )}
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
