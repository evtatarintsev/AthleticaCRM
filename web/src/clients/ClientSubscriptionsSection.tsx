import { useQuery } from "@tanstack/react-query";
import { TicketIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientId,
  LocalDate,
  MembershipId,
  MembershipSchema,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  ShowAllButton,
} from "./card/ClientCardBlock";
import { membershipsQuery } from "./card/clientCardQueries";
import {
  MembershipPill,
  MembershipSessions,
  MembershipSessionsBar,
} from "./membership/MembershipParts";
import {
  isCurrentMembership,
  membershipView,
  type MembershipView,
} from "./membership/membershipView";

/** Абонемент вместе с его состоянием на сегодня. */
interface MembershipItem {
  readonly membership: MembershipSchema;
  readonly view: MembershipView;
}

/** Порядок абонементов: неистёкшие раньше истёкших, внутри — по дате окончания, поздние выше. */
function compareMemberships(a: MembershipItem, b: MembershipItem): number {
  const currentA = isCurrentMembership(a.view);
  const currentB = isCurrentMembership(b.view);
  if (currentA !== currentB) {
    return currentA ? -1 : 1;
  }
  return b.membership.endDate.localeCompare(a.membership.endDate);
}

/**
 * Блок абонементов клиента: сначала неистёкшие, затем истёкшие; выдача — из шапки карточки.
 * Состояние абонементов определяется на дату [today]. С [onOpen] строка абонемента
 * кликабельна и открывает его панель.
 */
export function ClientSubscriptionsSection({
  api,
  branchId,
  clientId,
  limit,
  today = todayLocalDate(),
  onOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly limit: number;
  readonly today?: LocalDate;
  readonly onOpen?: (id: MembershipId) => void;
}) {
  const { t } = useI18n();
  const [showAll, setShowAll] = useState(false);
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const items = (memberships.data?.memberships ?? [])
    .map((membership) => ({ membership, view: membershipView(membership, today) }))
    .toSorted(compareMemberships);
  const shown = showAll ? items : items.slice(0, limit);

  return (
    <ClientCardBlock
      icon={<TicketIcon aria-hidden />}
      title={t("clients.detail.subscriptions")}
      count={items.length}
      footer={
        <ShowAllButton
          total={items.length}
          limit={limit}
          showAll={showAll}
          onToggle={() => {
            setShowAll(!showAll);
          }}
        />
      }
    >
      <BlockStatus
        isPending={memberships.isPending}
        isError={memberships.isError}
        isEmpty={items.length === 0}
        empty={t("clients.detail.subscriptionsEmpty")}
      />
      {shown.length > 0 && (
        <ul className="divide-y">
          {shown.map(({ membership, view }) => (
            <MembershipRow
              key={membership.id}
              membership={membership}
              view={view}
              onOpen={onOpen}
            />
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}

/**
 * Строка абонемента [membership] в состоянии [view]: название, срок, остаток занятий с полосой
 * прогресса и пометка состояния. С [onOpen] название — кнопка с зоной нажатия на всю строку.
 */
function MembershipRow({
  membership,
  view,
  onOpen,
}: {
  readonly membership: MembershipSchema;
  readonly view: MembershipView;
  readonly onOpen: ((id: MembershipId) => void) | undefined;
}) {
  const { t, format } = useI18n();
  const current = isCurrentMembership(view);
  return (
    <BlockRow
      className={cn(
        !current && "text-muted-foreground",
        onOpen !== undefined &&
          "relative transition-colors hover:bg-accent has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring has-[:focus-visible]:ring-inset",
      )}
      aside={
        <div className="space-y-1">
          <div className="font-medium tabular-nums">{format.money(membership.price)}</div>
          <MembershipPill phase={view.phase} />
        </div>
      }
    >
      {onOpen === undefined ? (
        <div className="font-medium">{membership.name}</div>
      ) : (
        <button
          type="button"
          aria-label={t("clientCard.membership.open", { name: membership.name })}
          aria-haspopup="dialog"
          onClick={() => {
            onOpen(membership.id);
          }}
          className="cursor-pointer text-left font-medium outline-none after:absolute after:inset-0"
        >
          {membership.name}
        </button>
      )}
      <BlockSub>
        {format.date(membership.startDate)} — {format.date(membership.endDate)} ·{" "}
        <MembershipSessions membership={membership} />
      </BlockSub>
      {current && <MembershipSessionsBar membership={membership} />}
    </BlockRow>
  );
}
