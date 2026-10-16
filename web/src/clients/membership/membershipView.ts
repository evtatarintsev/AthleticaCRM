import type { LocalDate, MembershipSchema } from "@/api/generated/contracts";

/**
 * Пометка абонемента для отображения, полученная из состояния с сервера:
 * не активирован, не начался, активен, заканчивается, к закрытию, закрыт, отозван.
 */
export type MembershipPhase =
  "inactive" | "upcoming" | "active" | "ending" | "closePending" | "closed" | "revoked";

/** Абонемент в виде для карточки клиента; все признаки — с сервера, клиент их не вычисляет. */
export interface MembershipView {
  /** Пометка абонемента. */
  readonly phase: MembershipPhase;
  /** Дата начала; `null` — абонемент не активировался. */
  readonly startDate: LocalDate | null;
  /** Дата окончания, последний день включительно; `null` — абонемент не активировался. */
  readonly endDate: LocalDate | null;
  /** Абонемент заканчивается по дате — подсвечивается дата окончания. */
  readonly endingByDate: boolean;
  /** Абонемент заканчивается по занятиям — подсвечивается остаток. */
  readonly endingBySessions: boolean;
  /** У активного абонемента вышел срок. */
  readonly expired: boolean;
  /** У активного абонемента закончились занятия. */
  readonly exhausted: boolean;
}

/** Вид абонемента [membership] по его состоянию [MembershipSchema.state] с сервера. */
export function membershipView(membership: MembershipSchema): MembershipView {
  const none = { endingByDate: false, endingBySessions: false, expired: false, exhausted: false };
  const { state } = membership;
  switch (state.type) {
    case "inactive":
      return { ...none, phase: "inactive", startDate: null, endDate: null };
    case "active": {
      const expired = state.termPosition === "EXPIRED";
      const exhausted = membership.sessionsRemaining === 0;
      const endingByDate = state.endingSoon.includes("BY_DATE");
      const endingBySessions = state.endingSoon.includes("BY_SESSIONS");
      return {
        phase: activePhase(
          state.termPosition,
          state.closePending,
          endingByDate || endingBySessions,
        ),
        startDate: state.startDate,
        endDate: state.endDate,
        endingByDate,
        endingBySessions,
        expired,
        exhausted,
      };
    }
    case "closed":
      return { ...none, phase: "closed", startDate: state.startDate, endDate: state.endDate };
    case "revoked":
      return { ...none, phase: "revoked", startDate: state.startDate, endDate: state.endDate };
  }
}

/** Пометка активного абонемента: «к закрытию» важнее «не начался» и «заканчивается». */
function activePhase(
  termPosition: "NOT_STARTED" | "IN_TERM" | "EXPIRED",
  closePending: boolean,
  ending: boolean,
): MembershipPhase {
  if (closePending) {
    return "closePending";
  }
  if (termPosition === "NOT_STARTED") {
    return "upcoming";
  }
  return ending ? "ending" : "active";
}

/**
 * Абонемент в виде [view] действует: не активирован или активен и не «к закрытию».
 * Закрытый, отозванный и «к закрытию» — нет.
 */
export function isCurrentMembership(view: MembershipView): boolean {
  switch (view.phase) {
    case "inactive":
    case "upcoming":
    case "active":
    case "ending":
      return true;
    case "closePending":
    case "closed":
    case "revoked":
      return false;
  }
}

/** Абонемент в виде [view] ещё не финальный: с ним доступны действия. */
export function isOpenMembership(view: MembershipView): boolean {
  return view.phase !== "closed" && view.phase !== "revoked";
}
