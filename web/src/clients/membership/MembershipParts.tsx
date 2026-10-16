import type { ReactNode } from "react";
import type { MembershipSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Pill } from "../card/ClientCardBlock";
import type { MembershipPhase, MembershipView } from "./membershipView";

/**
 * Фрагмент условий абонемента без переноса внутри; при [active] выделен как причина окончания.
 */
export function EndingMark({
  active,
  children,
}: {
  readonly active: boolean;
  readonly children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "whitespace-nowrap",
        active && "font-medium text-amber-700 dark:text-amber-400",
      )}
    >
      {children}
    </span>
  );
}

/** Пометка фазы абонемента [phase]: «Не начался», «Активен», «Заканчивается», «Истёк». */
export function MembershipPill({ phase }: { readonly phase: MembershipPhase }) {
  const { t } = useI18n();
  switch (phase) {
    case "upcoming":
      return <Pill tone="blue">{t("clientCard.subscriptionUpcoming")}</Pill>;
    case "active":
      return <Pill tone="green">{t("clients.detail.subscriptionActive")}</Pill>;
    case "ending":
      return <Pill tone="amber">{t("clientCard.subscriptionEnding")}</Pill>;
    case "expired":
      return <Pill tone="gray">{t("clients.detail.subscriptionExpired")}</Pill>;
  }
}

/**
 * Условия абонемента [membership] в одну строку: у начавшегося — остаток занятий
 * (или «Безлимит») и дата окончания, у не начавшегося — дата начала. Причина окончания
 * из [view] выделена цветом.
 */
export function MembershipTerms({
  membership,
  view,
}: {
  readonly membership: MembershipSchema;
  readonly view: MembershipView;
}) {
  const { t, format } = useI18n();
  if (view.phase === "upcoming") {
    return <>{t("clientCard.quick.from", { date: format.date(membership.startDate) })}</>;
  }
  return (
    <>
      <EndingMark active={view.endingBySessions}>
        <MembershipSessions membership={membership} />
      </EndingMark>
      {" · "}
      <EndingMark active={view.endingByDate}>
        {t("clientCard.quick.until", { date: format.date(membership.endDate) })}
      </EndingMark>
    </>
  );
}

/** Остаток занятий абонемента [membership]: «Осталось N из M» или «Безлимит». */
export function MembershipSessions({ membership }: { readonly membership: MembershipSchema }) {
  const { t } = useI18n();
  const total = membership.sessionsTotal;
  return (
    <>
      {total === null
        ? t("clients.detail.subscriptionUnlimited")
        : t("clients.detail.subscriptionSessions", {
            remaining: membership.sessionsRemaining ?? 0,
            total,
          })}
    </>
  );
}

/** Полоса остатка занятий абонемента [membership]; у безлимита не показывается. */
export function MembershipSessionsBar({ membership }: { readonly membership: MembershipSchema }) {
  const total = membership.sessionsTotal;
  if (total === null || total <= 0) {
    return null;
  }
  const remaining = membership.sessionsRemaining ?? 0;
  return (
    <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-secondary">
      <div
        className="h-full rounded-full bg-primary"
        style={{ width: `${String(Math.round((remaining / total) * 100))}%` }}
      />
    </div>
  );
}
