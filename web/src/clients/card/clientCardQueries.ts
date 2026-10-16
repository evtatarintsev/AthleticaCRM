import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientAttendanceItemSchema,
  ClientGroup,
  ClientId,
  MembershipId,
  ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { apiQuery } from "@/query/queries";

/** Сколько задач клиента загружается в карточку. */
const TASKS_LIMIT = 50;

/** На сколько дней вперёд показываются ближайшие занятия. */
const UPCOMING_DAYS = 14;

/** За сколько дней назад загружается история посещений. */
const ATTENDANCE_DAYS = 180;

/** Запрос абонементов клиента [clientId]; общий для блока и шапки. */
export function membershipsQuery(api: ApiClient, branchId: BranchId, clientId: ClientId) {
  return apiQuery(api, branchId, "memberships/list", { clientId });
}

/** Запрос истории абонемента [membershipId] для его панели. */
export function membershipHistoryQuery(
  api: ApiClient,
  branchId: BranchId,
  membershipId: MembershipId,
) {
  return apiQuery(api, branchId, "memberships/history", { membershipId });
}

/** Запрос задач клиента [clientId]; общий для блока и предупреждений шапки. */
export function clientTasksQuery(api: ApiClient, branchId: BranchId, clientId: ClientId) {
  return apiQuery(api, branchId, "tasks/list", { clientId, limit: TASKS_LIMIT });
}

/**
 * Запрос занятий групп [groups] на [UPCOMING_DAYS] дней вперёд; общий для блока и шапки.
 * Без групп не выполняется.
 */
export function clientUpcomingQuery(
  api: ApiClient,
  branchId: BranchId,
  groups: readonly ClientGroup[],
) {
  const today = todayLocalDate();
  return {
    ...apiQuery(api, branchId, "schedule/list", {
      from: today,
      to: addDays(today, UPCOMING_DAYS),
      groupIds: groups.map((group) => group.id),
    }),
    enabled: groups.length > 0,
  };
}

/** Запрос отметок посещаемости клиента [clientId] за [ATTENDANCE_DAYS] дней; общий для блоков и шапки. */
export function clientAttendanceQuery(api: ApiClient, branchId: BranchId, clientId: ClientId) {
  const today = todayLocalDate();
  return apiQuery(api, branchId, "attendance/client", {
    clientId,
    from: addDays(today, -ATTENDANCE_DAYS),
    to: today,
  });
}

/** Неотменённые занятия [sessions] по времени начала. */
export function upcomingSessions(
  sessions: readonly ScheduleSessionSchema[] | undefined,
): readonly ScheduleSessionSchema[] {
  return (sessions ?? [])
    .filter((session) => session.status !== "CANCELLED")
    .toSorted((a, b) => `${a.date}T${a.startTime}`.localeCompare(`${b.date}T${b.startTime}`));
}

/** Отметки [items] с присутствием [presence], от новых к старым. */
export function attendanceWith(
  items: readonly ClientAttendanceItemSchema[] | undefined,
  presence: ClientAttendanceItemSchema["presence"],
): readonly ClientAttendanceItemSchema[] {
  return (items ?? [])
    .filter((item) => item.presence === presence)
    .toSorted((a, b) => `${b.date}T${b.startTime}`.localeCompare(`${a.date}T${a.startTime}`));
}
