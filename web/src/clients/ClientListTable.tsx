import { Link } from "@tanstack/react-router";
import {
  createColumnHelper,
  rowSelectionFeature,
  tableFeatures,
  useTable,
  type RowSelectionState,
} from "@tanstack/react-table";
import { ClockIcon, PencilIcon, TicketIcon, TriangleAlertIcon } from "lucide-react";
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
import { ageOn } from "./card/clientOverview";
import {
  clientColumnKey,
  standardColumnLabelKey,
  type ClientColumn,
  type ClientColumnKey,
} from "./clientColumns";
import { ClientBalance } from "./ClientBalance";
import { clientFieldValue, contactValues, customFieldDisplay, sortableField } from "./clientCells";
import {
  clientListMembershipLabel,
  noMembershipLabel,
  type ClientListMembershipLabel,
} from "./clientListMembership";
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
 * На узком экране группы, абонементы и баланс подчиняются тем же настройкам колонок.
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
  const shown = (field: ClientColumnKey) =>
    props.columns.some((column) => column.kind === "standard" && column.field === field);
  const showGroups = shown("groups");
  const showMemberships = shown("memberships");

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
                    <div className="flex min-w-0 flex-col">
                      <Link
                        to="/clients/$clientId"
                        params={{ clientId: row.original.id }}
                        className="font-medium after:absolute after:inset-0 hover:underline"
                      >
                        {row.original.name}
                      </Link>
                      <ClientMeta client={row.original} />
                    </div>
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
            className="relative flex flex-col gap-2 px-3 py-2.5 data-[state=selected]:bg-primary/5"
          >
            <div className="flex items-center gap-3">
              <input
                type="checkbox"
                checked={row.getIsSelected()}
                onChange={row.getToggleSelectedHandler()}
                aria-label={t("directory.select", { name: row.original.name })}
                className="relative z-10 size-4 shrink-0 accent-primary"
              />
              <Avatar
                api={props.api}
                uploadId={row.original.avatarId}
                name={row.original.name}
                className="size-8"
              />
              <Link
                to="/clients/$clientId"
                params={{ clientId: row.original.id }}
                className="flex min-w-0 flex-1 flex-col after:absolute after:inset-0"
              >
                <span className="truncate font-medium">{row.original.name}</span>
                <ClientMeta client={row.original} />
              </Link>
              {shown("balance") && <BalanceCell client={row.original} />}
            </div>
            {(showGroups || showMemberships) && (
              <div className="flex flex-col items-start gap-1 rounded-lg bg-muted p-1.5 text-xs">
                {showGroups && (
                  <div className="flex flex-wrap gap-1">
                    {row.original.groups.length === 0 ? (
                      <span className="text-muted-foreground">—</span>
                    ) : (
                      row.original.groups.map((group) => (
                        <span
                          key={group.id}
                          className="inline-block max-w-full truncate rounded-md border bg-card px-2 leading-5.5 font-medium"
                        >
                          {group.name}
                        </span>
                      ))
                    )}
                  </div>
                )}
                {showMemberships && <MembershipLabels client={row.original} compact />}
              </div>
            )}
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
        <TableHead>{t(standardColumnLabelKey(column.field))}</TableHead>
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

/** Ячейка стандартной колонки [field] клиента [client]. */
function StandardColumnCell({
  field,
  client,
}: {
  readonly field: ClientColumnKey;
  readonly client: ClientListItem;
}) {
  switch (field) {
    case "balance":
      return (
        <div className="flex justify-end">
          <BalanceCell client={client} />
        </div>
      );
    case "groups":
      return <GroupChips client={client} />;
    case "memberships":
      return <MembershipLabels client={client} />;
  }
}

/** Пол и полный возраст клиента [client] на сегодня под именем: «Ж · 9 лет»; без даты рождения — пол. */
function ClientMeta({ client }: { readonly client: ClientListItem }) {
  const { t } = useI18n();
  const gender = t(`clients.genderShort.${client.gender}`);
  return (
    <span className="truncate text-sm text-muted-foreground">
      {client.birthday === null
        ? gender
        : `${gender} · ${t("clientCard.meta.age", { count: ageOn(client.birthday, new Date()) })}`}
    </span>
  );
}

/** Баланс клиента [client] и под ним число неоплаченных занятий красным, если они есть. */
function BalanceCell({ client }: { readonly client: ClientListItem }) {
  const { t } = useI18n();
  return (
    <div className="flex shrink-0 flex-col items-end gap-0.5 text-sm">
      <ClientBalance balance={client.balance} />
      {client.unpaidSessions > 0 && (
        <span className="text-xs font-semibold whitespace-nowrap text-destructive">
          {t("clients.unpaidSessions", { count: client.unpaidSessions })}
        </span>
      )}
    </div>
  );
}

/** Группы клиента [client] чипами-ссылками на страницу группы, друг под другом. */
function GroupChips({ client }: { readonly client: ClientListItem }) {
  if (client.groups.length === 0) {
    return <span className="text-muted-foreground">—</span>;
  }
  return (
    <div className="flex flex-col items-start gap-1.5">
      {client.groups.map((group) => (
        <Link
          key={group.id}
          to="/groups/$groupId"
          params={{ groupId: group.id }}
          className="relative z-10 inline-block h-6.5 max-w-48 truncate rounded-lg border bg-muted/50 px-2.5 text-xs leading-6 font-medium text-primary hover:border-foreground/20"
        >
          {group.name}
        </Link>
      ))}
    </div>
  );
}

/**
 * Метки абонементов клиента [client] друг под другом в порядке ответа сервера;
 * без абонементов — «Нет абонемента». [compact] — уменьшенные метки строки на телефоне.
 */
function MembershipLabels({
  client,
  compact = false,
}: {
  readonly client: ClientListItem;
  readonly compact?: boolean;
}) {
  const i18n = useI18n();
  const labels =
    client.memberships.length === 0
      ? [{ key: "none", label: noMembershipLabel(i18n) }]
      : client.memberships.map((membership) => ({
          key: membership.id,
          label: clientListMembershipLabel(membership, i18n),
        }));
  return (
    <div className={cn("flex max-w-full flex-col items-start", compact ? "gap-1" : "gap-1.5")}>
      {labels.map(({ key, label }) => (
        <MembershipPill key={key} label={label} compact={compact} />
      ))}
    </div>
  );
}

/** Метка абонемента [label]: цвет и значок по признакам. */
function MembershipPill({
  label,
  compact,
}: {
  readonly label: ClientListMembershipLabel;
  readonly compact: boolean;
}) {
  const Icon =
    label.icon === "ticket"
      ? TicketIcon
      : label.icon === "clock"
        ? ClockIcon
        : label.icon === "alert"
          ? TriangleAlertIcon
          : null;
  return (
    <span
      data-tone={label.tone}
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 truncate rounded-lg font-medium whitespace-nowrap tabular-nums",
        compact ? "h-6 px-2 text-xs" : "h-6.5 px-2.5 text-xs",
        label.tone === "ok" && "bg-primary/10 text-primary",
        label.tone === "warn" &&
          "bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
        label.tone === "start" &&
          "bg-emerald-50 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200",
        label.tone === "none" && "border bg-card text-muted-foreground",
      )}
    >
      {Icon !== null && <Icon aria-hidden className="size-3.5 shrink-0" />}
      <span className="truncate">{label.text}</span>
    </span>
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
