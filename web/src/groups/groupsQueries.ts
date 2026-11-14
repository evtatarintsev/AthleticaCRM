import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { BranchId, GroupId, GroupListRequest, LocalDate } from "@/api/generated/contracts";
import { apiQuery } from "@/query/queries";

/** Группы филиала [branchId] по фильтрам [request]. */
export function useGroups(api: ApiClient, branchId: BranchId, request: GroupListRequest) {
  return useQuery(apiQuery(api, branchId, "groups/list", request));
}

/** Карточка группы [groupId] в филиале [branchId]. */
export function useGroup(api: ApiClient, branchId: BranchId, groupId: GroupId) {
  return useQuery(apiQuery(api, branchId, "groups/detail", { id: groupId }));
}

/** Дисциплины филиала [branchId] для пикеров и фильтров группы. */
export function useGroupDisciplines(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "disciplines/list"), select: (r) => r.disciplines });
}

/** Сотрудники организации для пикеров и фильтров тренеров. */
export function useGroupEmployees(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "employees/list"), select: (r) => r.employees });
}

/** Залы филиала [branchId] для редактора расписания группы. */
export function useGroupHalls(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "halls/list"), select: (r) => r.halls });
}

/** Состав группы [groupId] с абонементом, долгом, балансом и причиной внимания. */
export function useGroupRoster(api: ApiClient, branchId: BranchId, groupId: GroupId) {
  return useQuery(apiQuery(api, branchId, "groups/roster", { groupId }));
}

/** Посещаемость группы [groupId] за период [from]..[to]; предыдущие данные видны до загрузки новых. */
export function useGroupAttendance(
  api: ApiClient,
  branchId: BranchId,
  groupId: GroupId,
  from: LocalDate,
  to: LocalDate,
) {
  return useQuery({
    ...apiQuery(api, branchId, "attendance/group", { groupId, from, to }),
    placeholderData: keepPreviousData,
  });
}
