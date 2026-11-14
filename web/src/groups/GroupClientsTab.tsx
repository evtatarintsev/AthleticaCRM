import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon, XIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientId,
  GroupRosterClient,
  GroupRosterResponse,
  MembershipId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { IssueSubscriptionSheet } from "@/clients/IssueSubscriptionSheet";
import { membershipsQuery } from "@/clients/card/clientCardQueries";
import { MembershipSheet } from "@/clients/membership/MembershipSheet";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import { attentionCounts, filterRoster, rosterAction, type RosterFilter } from "./groupOverview";

/** Открытая панель действия: выдача абонемента клиенту или абонемент клиента для погашения долга. */
type OpenSheet =
  | { readonly kind: "issue"; readonly clientId: ClientId }
  | { readonly kind: "repay"; readonly clientId: ClientId; readonly membershipId: MembershipId };

/**
 * Вкладка «Клиенты»: состав [roster] в порядке сервера с абонементом, балансом, причиной внимания
 * и действием по ней; поиск по имени и фильтры [filter] со счётчиками. Добавление — [onAdd],
 * удаление — [onRemove]; после закрытия панели действия состав обновляется через [onChanged].
 */
export function GroupClientsTab({
  api,
  branchId,
  roster,
  filter,
  onFilterChange,
  onAdd,
  onRemove,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly roster: UseQueryResult<GroupRosterResponse>;
  readonly filter: RosterFilter;
  readonly onFilterChange: (filter: RosterFilter) => void;
  readonly onAdd: () => void;
  readonly onRemove: (clientId: ClientId) => void;
  readonly onChanged: () => void;
}) {
  const { t } = useI18n();
  const [query, setQuery] = useState("");
  const [sheet, setSheet] = useState<OpenSheet | null>(null);
  const closeSheet = (open: boolean) => {
    if (!open) {
      setSheet(null);
      onChanged();
    }
  };

  if (roster.isError) {
    return <FormAlert message={t("groups.clients.loadError")} />;
  }
  if (roster.data === undefined) {
    return <Skeleton className="h-48 w-full" />;
  }
  const clients = roster.data.clients;
  const attention = attentionCounts(clients).total;
  const filters: readonly { readonly value: RosterFilter; readonly count: number }[] = [
    { value: "all", count: clients.length },
    { value: "attention", count: attention },
    { value: "ok", count: clients.length - attention },
  ];
  const shown = filterRoster(clients, filter, query);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div
          role="group"
          aria-label={t("groups.clients.filters")}
          className="inline-flex rounded-lg border bg-card p-0.5"
        >
          {filters.map(({ value, count }) => (
            <button
              key={value}
              type="button"
              aria-pressed={filter === value}
              onClick={() => {
                onFilterChange(value);
              }}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground",
                filter === value && "bg-foreground text-background",
              )}
            >
              {t(filterLabelKey(value))}
              <span className="ml-1.5 tabular-nums opacity-70">{count}</span>
            </button>
          ))}
        </div>
        <Input
          type="search"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
          }}
          placeholder={t("groups.clients.search")}
          aria-label={t("groups.clients.search")}
          className="w-full sm:w-56"
        />
        <Button variant="outline" className="sm:ml-auto" onClick={onAdd}>
          <PlusIcon aria-hidden />
          {t("groups.detail.addClients")}
        </Button>
      </div>

      {clients.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("groups.detail.clientsEmpty")}</p>
      ) : shown.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("groups.clients.noResults")}</p>
      ) : (
        <ul className="divide-y rounded-xl border bg-card">
          {shown.map((client) => (
            <RosterRow
              key={client.id}
              api={api}
              client={client}
              onRemove={onRemove}
              onOpenSheet={setSheet}
            />
          ))}
        </ul>
      )}

      {sheet?.kind === "issue" && (
        <IssueSubscriptionSheet
          api={api}
          branchId={branchId}
          clientId={sheet.clientId}
          open
          onOpenChange={closeSheet}
        />
      )}
      {sheet?.kind === "repay" && (
        <RepaySheet
          api={api}
          branchId={branchId}
          clientId={sheet.clientId}
          membershipId={sheet.membershipId}
          onOpenChange={closeSheet}
        />
      )}
    </div>
  );
}

/** Ключ подписи фильтра [filter]. */
function filterLabelKey(filter: RosterFilter) {
  switch (filter) {
    case "all":
      return "groups.clients.filterAll" as const;
    case "attention":
      return "groups.clients.filterAttention" as const;
    case "ok":
      return "groups.clients.filterOk" as const;
  }
}

/**
 * Строка участника [client]: имя со ссылкой на карточку, абонемент, баланс, причина внимания,
 * действие по ней и удаление из состава.
 */
function RosterRow({
  api,
  client,
  onRemove,
  onOpenSheet,
}: {
  readonly api: ApiClient;
  readonly client: GroupRosterClient;
  readonly onRemove: (clientId: ClientId) => void;
  readonly onOpenSheet: (sheet: OpenSheet) => void;
}) {
  const { t, format } = useI18n();
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-sm">
      <Link
        to="/clients/$clientId"
        params={{ clientId: client.id }}
        className="flex min-w-0 flex-[1_1_12rem] items-center gap-2.5 font-medium hover:underline"
      >
        <Avatar api={api} uploadId={client.avatarId} name={client.name} className="size-7" />
        <span className="truncate">{client.name}</span>
      </Link>
      <span className="min-w-0 flex-[2_1_16rem] text-muted-foreground">
        <MembershipText client={client} />
      </span>
      <span
        className={cn(
          "tabular-nums",
          client.balance.minorUnits < 0
            ? "font-semibold text-destructive"
            : "text-muted-foreground",
        )}
      >
        {t("groups.clients.balance", { amount: format.money(client.balance) })}
      </span>
      {client.attention !== null && (
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            client.attention === "DEBT"
              ? "bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-200"
              : "bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
          )}
        >
          {t(`groups.attention.${client.attention}`)}
          {client.unpaidSessions > 0 &&
            ` · ${t("groups.clients.unpaid", { count: client.unpaidSessions })}`}
        </span>
      )}
      <span className="ml-auto flex items-center gap-1">
        <RowAction client={client} onOpenSheet={onOpenSheet} />
        <button
          type="button"
          aria-label={t("groups.detail.removeClientAria", { name: client.name })}
          onClick={() => {
            onRemove(client.id);
          }}
          className="rounded-full p-2 text-muted-foreground hover:text-destructive"
        >
          <XIcon aria-hidden className="size-4" />
        </button>
      </span>
    </li>
  );
}

/** Абонемент участника [client]: название, остаток или безлимит, дата окончания или «нет абонемента». */
function MembershipText({ client }: { readonly client: GroupRosterClient }) {
  const { t, format } = useI18n();
  const membership = client.membership;
  if (membership === null) {
    return t("groups.clients.noMembership");
  }
  const parts = [membership.name];
  if (!membership.activated) {
    parts.push(t("groups.clients.notActivated"));
  }
  parts.push(
    membership.sessionsRemaining === null
      ? t("groups.clients.unlimited")
      : t("groups.clients.remaining", { count: membership.sessionsRemaining }),
  );
  if (membership.endDate !== null) {
    parts.push(t("groups.clients.until", { date: format.date(membership.endDate) }));
  }
  return parts.join(" · ");
}

/** Действие по причине внимания участника [client]; без причины — ничего. */
function RowAction({
  client,
  onOpenSheet,
}: {
  readonly client: GroupRosterClient;
  readonly onOpenSheet: (sheet: OpenSheet) => void;
}) {
  const { t } = useI18n();
  const action = rosterAction(client);
  switch (action) {
    case null:
      return null;
    case "openClient":
      return (
        <Button variant="outline" size="sm" asChild>
          <Link to="/clients/$clientId" params={{ clientId: client.id }}>
            {t("groups.clients.action.openClient")}
          </Link>
        </Button>
      );
    case "repay": {
      const membership = client.membership;
      return (
        membership !== null && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              onOpenSheet({ kind: "repay", clientId: client.id, membershipId: membership.id });
            }}
          >
            {t("groups.clients.action.repay")}
          </Button>
        )
      );
    }
    case "issue":
    case "issueNext":
      return (
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            onOpenSheet({ kind: "issue", clientId: client.id });
          }}
        >
          {t(`groups.clients.action.${action}`)}
        </Button>
      );
  }
}

/** Панель абонемента [membershipId] клиента [clientId] для погашения долга; абонемент загружается. */
function RepaySheet({
  api,
  branchId,
  clientId,
  membershipId,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  return (
    <MembershipSheet
      membership={memberships.data?.memberships.find((m) => m.id === membershipId) ?? null}
      open
      api={api}
      branchId={branchId}
      clientId={clientId}
      onOpenChange={onOpenChange}
    />
  );
}
