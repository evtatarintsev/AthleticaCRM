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

/**
 * Пометка абонемента [phase]: «Не активирован», «Не начался», «Активен», «Заканчивается»,
 * «К закрытию», «Закрыт», «Отозван».
 */
export function MembershipPill({ phase }: { readonly phase: MembershipPhase }) {
  const { t } = useI18n();
  switch (phase) {
    case "inactive":
      return <Pill tone="blue">{t("membership.phase.inactive")}</Pill>;
    case "upcoming":
      return <Pill tone="blue">{t("clientCard.subscriptionUpcoming")}</Pill>;
    case "active":
      return <Pill tone="green">{t("clients.detail.subscriptionActive")}</Pill>;
    case "ending":
      return <Pill tone="amber">{t("clientCard.subscriptionEnding")}</Pill>;
    case "closePending":
      return <Pill tone="amber">{t("membership.phase.closePending")}</Pill>;
    case "closed":
      return <Pill tone="gray">{t("membership.phase.closed")}</Pill>;
    case "revoked":
      return <Pill tone="gray">{t("membership.phase.revoked")}</Pill>;
  }
}

/**
 * Режим активации неактивированного абонемента [membership]: «Активируется {дата}»
 * или «При первом посещении» с крайней датой, если она задана.
 */
export function MembershipActivationNote({
  membership,
}: {
  readonly membership: MembershipSchema;
}) {
  const { t, format } = useI18n();
  const { state } = membership;
  if (state.type !== "inactive") {
    return null;
  }
  if (state.activateOn !== null) {
    return <>{t("membership.activatesOn", { date: format.date(state.activateOn) })}</>;
  }
  return (
    <>
      {membership.activationDeadline === null
        ? t("membership.activation.ON_FIRST_VISIT")
        : t("membership.firstVisitUntil", { date: format.date(membership.activationDeadline) })}
    </>
  );
}

/** Причина «к закрытию» по виду [view]: «Срок вышел» и/или «Занятия закончились». */
export function ClosePendingReasons({ view }: { readonly view: MembershipView }) {
  const { t } = useI18n();
  const reasons = [
    view.expired ? t("membership.expired") : null,
    view.exhausted ? t("membership.exhausted") : null,
  ].filter((reason) => reason !== null);
  if (view.phase !== "closePending" || reasons.length === 0) {
    return null;
  }
  return <span className="text-amber-700 dark:text-amber-400">{reasons.join(" · ")}</span>;
}

/**
 * Условия абонемента [membership] в одну строку: у неактивированного — режим активации,
 * у не начавшегося — дата начала, иначе остаток занятий (или «Безлимит») и дата окончания.
 * Причина «заканчивается» из [view] выделена цветом.
 */
export function MembershipTerms({
  membership,
  view,
}: {
  readonly membership: MembershipSchema;
  readonly view: MembershipView;
}) {
  const { t, format } = useI18n();
  if (view.startDate === null || view.endDate === null) {
    return <MembershipActivationNote membership={membership} />;
  }
  if (view.phase === "upcoming") {
    return <>{t("clientCard.quick.from", { date: format.date(view.startDate) })}</>;
  }
  return (
    <>
      <EndingMark active={view.endingBySessions}>
        <MembershipSessions membership={membership} />
      </EndingMark>
      {" · "}
      <EndingMark active={view.endingByDate}>
        {t("clientCard.quick.until", { date: format.date(view.endDate) })}
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

/**
 * Заморозка абонемента [membership] с сервера: «Заморожен до {дата}» для действующей,
 * «Заморозка с {дата}» для запланированной; без заморозки — ничего.
 */
export function FreezeNote({ membership }: { readonly membership: MembershipSchema }) {
  const { t, format } = useI18n();
  const { state } = membership;
  if (state.type !== "active" || state.freeze === null) {
    return null;
  }
  switch (state.freeze.status) {
    case "ACTIVE":
      return <>{t("membership.freeze.activeUntil", { date: format.date(state.freeze.endDate) })}</>;
    case "SCHEDULED":
      return (
        <>{t("membership.freeze.scheduledFrom", { date: format.date(state.freeze.startDate) })}</>
      );
  }
}
