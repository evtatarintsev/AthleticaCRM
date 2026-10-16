import type { LocalDate, MembershipSchema } from "@/api/generated/contracts";
import { addDays } from "@/lib/localDate";

/** За сколько дней до окончания абонемент считается заканчивающимся. */
const ENDING_DAYS = 7;

/** Сколько занятий и меньше должно остаться, чтобы абонемент считался заканчивающимся. */
const ENDING_SESSIONS = 1;

/**
 * Фаза абонемента для отображения: не начался, действует, заканчивается, истёк.
 */
export type MembershipPhase = "upcoming" | "active" | "ending" | "expired";

/** Состояние абонемента для отображения в карточке клиента. */
export interface MembershipView {
  /** Фаза абонемента на сегодняшнюю дату. */
  readonly phase: MembershipPhase;
  /** Абонемент заканчивается по сроку; `true` только в фазе `ending`. */
  readonly endingByDate: boolean;
  /** Абонемент заканчивается по остатку занятий; `true` только в фазе `ending`. */
  readonly endingBySessions: boolean;
}

/**
 * Состояние абонемента [membership] на дату [today]: истёк, если дата окончания раньше
 * сегодняшней; не начался, если дата начала позже; заканчивается, если до окончания не больше
 * [ENDING_DAYS] дней или осталось не больше [ENDING_SESSIONS] занятий; иначе действует.
 *
 * Временно: состояние абонемента — правило домена, и сервер начнёт отдавать его сам
 * (https://github.com/evtatarintsev/AthleticaCRM/issues/162). После этого функция удаляется,
 * а отображение читает поле схемы.
 */
export function membershipView(membership: MembershipSchema, today: LocalDate): MembershipView {
  if (membership.endDate < today) {
    return { phase: "expired", endingByDate: false, endingBySessions: false };
  }
  if (membership.startDate > today) {
    return { phase: "upcoming", endingByDate: false, endingBySessions: false };
  }
  const endingByDate = membership.endDate <= addDays(today, ENDING_DAYS);
  const endingBySessions =
    membership.sessionsTotal !== null && (membership.sessionsRemaining ?? 0) <= ENDING_SESSIONS;
  return {
    phase: endingByDate || endingBySessions ? "ending" : "active",
    endingByDate,
    endingBySessions,
  };
}

/** Абонемент в фазе [view] ещё в силе: действует, заканчивается или не начался. */
export function isCurrentMembership(view: MembershipView): boolean {
  return view.phase !== "expired";
}
