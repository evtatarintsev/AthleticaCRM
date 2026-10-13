import { useQuery } from "@tanstack/react-query";
import { TicketIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientId, MembershipSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
  ShowAllButton,
} from "./card/ClientCardBlock";
import { membershipEndingSoon } from "./card/clientCardAlerts";
import { membershipsQuery } from "./card/clientCardQueries";

/** Порядок абонементов: действующие раньше истёкших, внутри — по дате окончания, поздние выше. */
function compareMemberships(a: MembershipSchema, b: MembershipSchema): number {
  const activeA = a.status === "ACTIVE";
  const activeB = b.status === "ACTIVE";
  if (activeA !== activeB) {
    return activeA ? -1 : 1;
  }
  return b.endDate.localeCompare(a.endDate);
}

/** Блок абонементов клиента: сначала действующие, затем истёкшие; выдача — из шапки карточки. */
export function ClientSubscriptionsSection({
  api,
  branchId,
  clientId,
  limit,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly limit: number;
}) {
  const { t } = useI18n();
  const [showAll, setShowAll] = useState(false);
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const items = (memberships.data?.memberships ?? []).toSorted(compareMemberships);
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
          {shown.map((membership) => (
            <MembershipRow key={membership.id} membership={membership} />
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}

/** Строка абонемента: название, срок, остаток занятий с полосой прогресса и состояние. */
function MembershipRow({ membership }: { readonly membership: MembershipSchema }) {
  const { t, format } = useI18n();
  const active = membership.status === "ACTIVE";
  const total = membership.sessionsTotal;
  const remaining = membership.sessionsRemaining ?? 0;
  const sessions =
    total === null
      ? t("clients.detail.subscriptionUnlimited")
      : t("clients.detail.subscriptionSessions", { remaining, total });
  return (
    <BlockRow
      className={cn(!active && "text-muted-foreground")}
      aside={
        <div className="space-y-1">
          <div className="font-medium tabular-nums">{format.money(membership.price)}</div>
          {!active ? (
            <Pill tone="gray">{t("clients.detail.subscriptionExpired")}</Pill>
          ) : membershipEndingSoon(membership) ? (
            <Pill tone="amber">{t("clientCard.subscriptionEnding")}</Pill>
          ) : (
            <Pill tone="green">{t("clients.detail.subscriptionActive")}</Pill>
          )}
        </div>
      }
    >
      <div className="font-medium">{membership.name}</div>
      <BlockSub>
        {format.date(membership.startDate)} — {format.date(membership.endDate)} · {sessions}
      </BlockSub>
      {active && total !== null && total > 0 && (
        <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-secondary">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${String(Math.round((remaining / total) * 100))}%` }}
          />
        </div>
      )}
    </BlockRow>
  );
}
