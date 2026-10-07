import { z } from "zod";
import {
  DisciplineIdSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  HallIdSchema,
  LocalDateSchema,
  type DisciplineId,
  type EmployeeId,
  type GroupId,
  type HallId,
  type LocalDate,
  type ScheduleListRequest,
} from "@/api/generated/contracts";
import { addDays, mondayOf, todayLocalDate } from "@/lib/localDate";

/** Search-параметры адреса `/schedule`: неделя и фильтры. Отсутствие поля — неделя с сегодняшним днём и без фильтра. */
export const ScheduleSearchSchema = z.object({
  weekStart: LocalDateSchema.optional().catch(undefined),
  disciplineIds: z.array(DisciplineIdSchema).optional().catch(undefined),
  hallIds: z.array(HallIdSchema).optional().catch(undefined),
  employeeIds: z.array(EmployeeIdSchema).optional().catch(undefined),
  groupIds: z.array(GroupIdSchema).optional().catch(undefined),
});

/** Search-параметры адреса расписания. */
export type ScheduleSearch = z.output<typeof ScheduleSearchSchema>;

/** Фильтры расписания без недели. */
export interface ScheduleFilters {
  readonly disciplineIds: readonly DisciplineId[];
  readonly hallIds: readonly HallId[];
  readonly employeeIds: readonly EmployeeId[];
  /** Группы; задаются только переходом из карточки группы. */
  readonly groupIds: readonly GroupId[];
}

/** Понедельник отображаемой недели из search-параметров [search]; по умолчанию — текущая неделя. */
export function weekStartOf(search: ScheduleSearch): LocalDate {
  return search.weekStart ?? mondayOf(todayLocalDate());
}

/** Фильтры из search-параметров [search] с подставленными значениями по умолчанию. */
export function scheduleFiltersOf(search: ScheduleSearch): ScheduleFilters {
  return {
    disciplineIds: search.disciplineIds ?? [],
    hallIds: search.hallIds ?? [],
    employeeIds: search.employeeIds ?? [],
    groupIds: search.groupIds ?? [],
  };
}

/** Search-параметры для недели [weekStart] и фильтров [filters]: только отличия от умолчания. */
export function scheduleSearchOf(weekStart: LocalDate, filters: ScheduleFilters): ScheduleSearch {
  return {
    ...(weekStart === mondayOf(todayLocalDate()) ? {} : { weekStart }),
    ...(filters.disciplineIds.length === 0 ? {} : { disciplineIds: [...filters.disciplineIds] }),
    ...(filters.hallIds.length === 0 ? {} : { hallIds: [...filters.hallIds] }),
    ...(filters.employeeIds.length === 0 ? {} : { employeeIds: [...filters.employeeIds] }),
    ...(filters.groupIds.length === 0 ? {} : { groupIds: [...filters.groupIds] }),
  };
}

/** Запрос `/schedule/list` за неделю, начинающуюся [weekStart], с фильтрами [filters]. */
export function scheduleListRequest(
  weekStart: LocalDate,
  filters: ScheduleFilters,
): ScheduleListRequest {
  return {
    from: weekStart,
    to: addDays(weekStart, 6),
    ...(filters.disciplineIds.length === 0 ? {} : { disciplineIds: filters.disciplineIds }),
    ...(filters.hallIds.length === 0 ? {} : { hallIds: filters.hallIds }),
    ...(filters.employeeIds.length === 0 ? {} : { employeeIds: filters.employeeIds }),
    ...(filters.groupIds.length === 0 ? {} : { groupIds: filters.groupIds }),
  };
}

/** Активных фильтров расписания, для бейджей и сброса. */
export function activeScheduleFilterCount(filters: ScheduleFilters): number {
  return (
    (filters.disciplineIds.length > 0 ? 1 : 0) +
    (filters.hallIds.length > 0 ? 1 : 0) +
    (filters.employeeIds.length > 0 ? 1 : 0) +
    (filters.groupIds.length > 0 ? 1 : 0)
  );
}
