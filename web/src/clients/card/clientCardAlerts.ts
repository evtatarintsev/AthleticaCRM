import {
  LocalDateSchema,
  type ClientDetailResponse,
  type LocalDate,
  type MembershipSchema,
  type Money,
  type TaskListItemSchema,
} from "@/api/generated/contracts";
import { addDays, todayLocalDate } from "@/lib/localDate";

/** За сколько дней до окончания абонемент считается заканчивающимся. */
const ENDING_DAYS = 7;

/** За сколько дней до дня рождения показывается предупреждение. */
const BIRTHDAY_DAYS = 7;

/** Предупреждение в шапке карточки — вычисляемый сигнал, требующий внимания сотрудника. */
export type ClientAlert =
  | { readonly type: "archived" }
  | { readonly type: "debt"; readonly amount: Money }
  | { readonly type: "subscriptionEnding"; readonly name: string; readonly until: LocalDate }
  | { readonly type: "noActiveSubscription" }
  | { readonly type: "overdueTasks"; readonly count: number }
  | { readonly type: "birthday"; readonly on: LocalDate };

/**
 * Действующий абонемент [membership] заканчивается: до конца срока не больше [ENDING_DAYS]
 * дней на дату [today] или осталось не больше одного занятия.
 */
export function membershipEndingSoon(
  membership: MembershipSchema,
  today: LocalDate = todayLocalDate(),
): boolean {
  if (membership.status !== "ACTIVE") {
    return false;
  }
  const byDate = membership.endDate <= addDays(today, ENDING_DAYS);
  const bySessions = membership.sessionsTotal !== null && (membership.sessionsRemaining ?? 0) <= 1;
  return byDate || bySessions;
}

/** Ближайший на дату [today] день рождения по дате рождения [birthday] (29 февраля — 1 марта). */
export function nextBirthday(birthday: LocalDate, today: LocalDate): LocalDate {
  const year = Number(today.slice(0, 4));
  const thisYear = birthdayIn(birthday, year);
  return thisYear >= today ? thisYear : birthdayIn(birthday, year + 1);
}

/** День рождения [birthday] в году [year]; несуществующее 29 февраля переносится на 1 марта. */
function birthdayIn(birthday: LocalDate, year: number): LocalDate {
  const iso = `${String(year)}-${birthday.slice(5)}`;
  const exists = new Date(`${iso}T00:00:00Z`).toISOString().startsWith(iso);
  return LocalDateSchema.parse(exists ? iso : `${String(year)}-03-01`);
}

/** Исходные данные для предупреждений; `undefined` — данные ещё не загружены или недоступны. */
export interface ClientAlertSources {
  readonly client: ClientDetailResponse;
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly tasks: readonly TaskListItemSchema[] | undefined;
  readonly today: LocalDate;
  readonly now: Date;
}

/** Предупреждения карточки по данным [sources] в порядке важности. */
export function clientAlerts(sources: ClientAlertSources): readonly ClientAlert[] {
  const { client, memberships, tasks, today, now } = sources;
  const alerts: ClientAlert[] = [];
  if (client.state === "ARCHIVED") {
    alerts.push({ type: "archived" });
  }
  if (client.balance.minorUnits < 0) {
    alerts.push({ type: "debt", amount: client.balance });
  }
  if (memberships !== undefined) {
    const active = memberships.filter((membership) => membership.status === "ACTIVE");
    const ending = active.find((membership) => membershipEndingSoon(membership, today));
    if (ending !== undefined) {
      alerts.push({ type: "subscriptionEnding", name: ending.name, until: ending.endDate });
    }
    if (active.length === 0 && client.groups.length > 0 && client.state === "ACTIVE") {
      alerts.push({ type: "noActiveSubscription" });
    }
  }
  if (tasks !== undefined) {
    const overdue = tasks.filter(
      (task) =>
        task.status !== "COMPLETED" &&
        task.dueDate !== null &&
        Date.parse(task.dueDate) < now.getTime(),
    ).length;
    if (overdue > 0) {
      alerts.push({ type: "overdueTasks", count: overdue });
    }
  }
  if (client.birthday !== null) {
    const on = nextBirthday(client.birthday, today);
    if (on <= addDays(today, BIRTHDAY_DAYS)) {
      alerts.push({ type: "birthday", on });
    }
  }
  return alerts;
}
