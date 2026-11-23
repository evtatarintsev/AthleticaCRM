import type { ClientListMembership, LocalDate } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Цвет метки абонемента: действует, заканчивается или к закрытию, ещё не начался, нет абонемента. */
export type ClientListMembershipTone = "ok" | "warn" | "start" | "none";

/** Значок метки абонемента: билет, часы, предупреждение; `null` — без значка. */
export type ClientListMembershipIcon = "ticket" | "clock" | "alert" | null;

/** Метка абонемента в строке списка клиентов. */
export interface ClientListMembershipLabel {
  /** Цвет метки. */
  readonly tone: ClientListMembershipTone;
  /** Значок перед текстом. */
  readonly icon: ClientListMembershipIcon;
  /** Текст метки: название абонемента и признаки через « · ». */
  readonly text: string;
}

/** Метка «Нет абонемента» для клиента без активных и неактивированных абонементов. */
export function noMembershipLabel({ t }: Pick<I18n, "t">): ClientListMembershipLabel {
  return { tone: "none", icon: null, text: t("clients.membership.none") };
}

/**
 * Метка абонемента [membership] по признакам с сервера, по первому подходящему правилу:
 * к закрытию → не начался или не активирован → заканчивается → действует.
 */
export function clientListMembershipLabel(
  membership: ClientListMembership,
  { t, format }: Pick<I18n, "t" | "format">,
): ClientListMembershipLabel {
  const label = (
    tone: ClientListMembershipTone,
    icon: ClientListMembershipIcon,
    parts: readonly string[],
  ): ClientListMembershipLabel => ({ tone, icon, text: [membership.name, ...parts].join(" · ") });
  const until = (date: LocalDate) =>
    t("clients.membership.until", { date: format.dayMonthShort(date) });
  const sessions =
    membership.sessionsTotal === null || membership.sessionsRemaining === null
      ? t("clients.membership.unlimited")
      : t("clients.membership.sessions", {
          remaining: membership.sessionsRemaining,
          total: membership.sessionsTotal,
        });
  const { state } = membership;
  switch (state.type) {
    case "inactive":
      return label("start", "clock", [
        state.activationMode === "ON_FIRST_VISIT" || state.activateOn === null
          ? t("clients.membership.firstVisit")
          : t("clients.membership.startsOn", { date: format.dayMonthShort(state.activateOn) }),
      ]);
    case "active": {
      if (state.closePending) {
        return label("warn", "alert", [
          t("clients.membership.closePending"),
          state.termPosition === "EXPIRED"
            ? t("clients.membership.expired")
            : t("clients.membership.exhausted"),
        ]);
      }
      if (state.termPosition === "NOT_STARTED") {
        return label("start", "clock", [
          t("clients.membership.startsOn", { date: format.dayMonthShort(state.startDate) }),
        ]);
      }
      if (state.endingSoon.length > 0) {
        return label("warn", "clock", [
          t("clients.membership.ending"),
          ...(state.endingSoon.includes("BY_SESSIONS") ? [sessions] : []),
          ...(state.endingSoon.includes("BY_DATE") ? [until(state.endDate)] : []),
        ]);
      }
      return label("ok", "ticket", [sessions, until(state.endDate)]);
    }
  }
}
