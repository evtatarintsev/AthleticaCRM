import {
  LocalDateSchema,
  type ClientDetailResponse,
  type LocalDate,
  type MembershipSchema,
  type Money,
  type TaskListResponse,
  type UnpaidSessionSchema,
} from "@/api/generated/contracts";
import { addDays } from "@/lib/localDate";
import { currentMemberships } from "./clientOverview";

export type ClientAlert =
  | {
      readonly type: "debt";
      readonly amount: Money;
      readonly sessions: readonly UnpaidSessionSchema[];
      readonly membership: MembershipSchema | null;
      readonly repayLimit: number;
    }
  | { readonly type: "noActiveSubscription" }
  | {
      readonly type: "overdueTasks";
      readonly count: number;
      readonly first: TaskListResponse["tasks"][number] | undefined;
    }
  | { readonly type: "birthday"; readonly on: LocalDate };

export function nextBirthday(birthday: LocalDate, today: LocalDate): LocalDate {
  const year = Number(today.slice(0, 4));
  const thisYear = birthdayIn(birthday, year);
  return thisYear >= today ? thisYear : birthdayIn(birthday, year + 1);
}
function birthdayIn(birthday: LocalDate, year: number): LocalDate {
  const iso = `${String(year)}-${birthday.slice(5)}`;
  const exists = new Date(`${iso}T00:00:00Z`).toISOString().startsWith(iso);
  return LocalDateSchema.parse(exists ? iso : `${String(year)}-03-01`);
}
export interface ClientAlertSources {
  readonly client: ClientDetailResponse;
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly unpaid: readonly UnpaidSessionSchema[] | undefined;
  readonly overdue: TaskListResponse | undefined;
  readonly today: LocalDate;
}

export function clientAlerts({
  client,
  memberships,
  unpaid = [],
  overdue,
  today,
}: ClientAlertSources): readonly ClientAlert[] {
  const alerts: ClientAlert[] = [];
  const current = currentMemberships(memberships);
  if (unpaid.length > 0 || client.balance.minorUnits < 0) {
    const membership =
      current.find(
        (item) =>
          item.state.type === "active" &&
          (item.sessionsRemaining === null || item.sessionsRemaining > 0) &&
          unpaid.some((session) => item.groups.some((group) => group.id === session.groupId)),
      ) ?? null;
    const covered = unpaid.filter(
      (session) => membership?.groups.some((group) => group.id === session.groupId) === true,
    ).length;
    alerts.push({
      type: "debt",
      amount: client.balance,
      sessions: unpaid,
      membership,
      repayLimit: Math.min(covered, membership?.sessionsRemaining ?? covered),
    });
  }
  if (
    memberships !== undefined &&
    current.length === 0 &&
    client.groups.length > 0 &&
    client.state === "ACTIVE" &&
    unpaid.length === 0
  )
    alerts.push({ type: "noActiveSubscription" });
  if (overdue !== undefined && overdue.total > 0)
    alerts.push({ type: "overdueTasks", count: overdue.total, first: overdue.tasks[0] });
  if (client.birthday !== null) {
    const on = nextBirthday(client.birthday, today);
    if (on <= addDays(today, 7)) alerts.push({ type: "birthday", on });
  }
  return alerts;
}
