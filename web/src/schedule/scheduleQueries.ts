import { useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { BranchId, ScheduleListRequest } from "@/api/generated/contracts";
import { apiQuery } from "@/query/queries";

/** Занятия недели по запросу [request] в филиале [branchId]. */
export function useSchedule(api: ApiClient, branchId: BranchId, request: ScheduleListRequest) {
  return useQuery(apiQuery(api, branchId, "schedule/list", request));
}

/** Дисциплины филиала [branchId] для фильтра расписания. */
export function useScheduleDisciplines(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "disciplines/list"), select: (r) => r.disciplines });
}

/** Залы филиала [branchId] для фильтра расписания. */
export function useScheduleHalls(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "halls/list"), select: (r) => r.halls });
}

/** Сотрудники организации для фильтра расписания по тренерам. */
export function useScheduleEmployees(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "employees/list"), select: (r) => r.employees });
}

/**
 * Группы филиала [branchId] для подписи активного фильтра по группе; запрашиваются,
 * только когда фильтр задан ([enabled]).
 */
export function useScheduleGroups(api: ApiClient, branchId: BranchId, enabled: boolean) {
  return useQuery({ ...apiQuery(api, branchId, "groups/list-for-select"), enabled });
}

/**
 * Часовой пояс организации из `org/settings`: в нём расписание считает «сейчас» и «сегодня».
 * Пока пояс не получен, страница показывает загрузку, при ошибке — повтор.
 */
export function useOrgTimeZone(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "org/settings"), select: (r) => r.timezone });
}
