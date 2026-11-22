import { queryOptions } from "@tanstack/react-query";
import { InstantSchema } from "@/api/generated/contracts";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  TaskStatus,
  ClientAttendanceItemSchema,
  ClientGroup,
  ClientId,
  MembershipId,
  ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { unwrap } from "@/query/apiFailure";
import { apiQuery } from "@/query/queries";

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

/** Запрос неоплаченных занятий клиента в группах абонемента [membershipId]. */
export function unpaidSessionsQuery(
  api: ApiClient,
  branchId: BranchId,
  membershipId: MembershipId,
) {
  return apiQuery(api, branchId, "memberships/unpaid-sessions", { membershipId });
}

/** Запрос общего числа задач клиента [clientId]. */
export function clientTasksQuery(api: ApiClient, branchId: BranchId, clientId: ClientId) {
  return apiQuery(api, branchId, "tasks/list", { clientId, limit: 1 });
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

/** Неотменённые занятия, которые ещё не начались, по местному времени браузера. */
export function upcomingSessions(
  sessions: readonly ScheduleSessionSchema[] | undefined,
  now = new Date(),
): readonly ScheduleSessionSchema[] {
  return (sessions ?? [])
    .filter(
      (session) =>
        session.status !== "CANCELLED" &&
        new Date(`${session.date}T${session.startTime}`).getTime() > now.getTime(),
    )
    .toSorted((a, b) => `${a.date}T${a.startTime}`.localeCompare(`${b.date}T${b.startTime}`));
}

export function clientCardSectionsQuery(api: ApiClient, branchId: BranchId) {
  return { ...apiQuery(api, branchId, "org/client-card"), staleTime: 5 * 60 * 1000 };
}

export function clientUnpaidSessionsQuery(api: ApiClient, branchId: BranchId, clientId: ClientId) {
  return apiQuery(api, branchId, "clients/unpaid-sessions", { clientId });
}

/** Время просрочки вычисляется при обновлении, а не включается в ключ каждого рендера. */
export function clientTaskSummaryQuery(
  api: ApiClient,
  branchId: BranchId,
  clientId: ClientId,
  overdue: boolean,
) {
  const statuses: readonly TaskStatus[] = ["PENDING", "IN_PROGRESS", "PAUSED"];
  return queryOptions({
    queryKey: [
      "api",
      branchId,
      "tasks/list",
      { clientId, summary: overdue ? "overdue" : "unfinished" },
    ] as const,
    queryFn: async () => {
      return unwrap(
        await api.call("tasks/list", {
          clientId,
          statuses,
          limit: 1,
          ...(overdue ? { dueDateTo: InstantSchema.parse(new Date().toISOString()) } : {}),
        }),
      );
    },
  });
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
