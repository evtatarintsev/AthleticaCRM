import { z } from "zod";
import {
  DisciplineIdSchema,
  EmployeeIdSchema,
  type DisciplineId,
  type EmployeeId,
  type GroupListRequest,
} from "@/api/generated/contracts";

/** Полное состояние фильтров списка групп. */
export interface GroupListFilters {
  /** Поиск по названию. */
  readonly q: string;
  /** Фильтр по дисциплинам: группа входит, если у неё есть хотя бы одна из них. */
  readonly disciplineIds: readonly DisciplineId[];
  /** Фильтр по тренерам. */
  readonly employeeIds: readonly EmployeeId[];
}

/**
 * Search-параметры адреса `/groups`. В адресе хранятся только отличия от значений
 * по умолчанию, некорректное значение сбрасывается к умолчанию, а не роняет страницу.
 */
export const GroupListSearchSchema = z.object({
  q: z.string().optional().catch(undefined),
  disciplineIds: z.array(DisciplineIdSchema).optional().catch(undefined),
  employeeIds: z.array(EmployeeIdSchema).optional().catch(undefined),
});

/** Search-параметры адреса списка групп. */
export type GroupListSearch = z.output<typeof GroupListSearchSchema>;

/** Полное состояние фильтров из search-параметров [search] с подставленными значениями по умолчанию. */
export function groupListFilters(search: GroupListSearch): GroupListFilters {
  return {
    q: search.q ?? "",
    disciplineIds: search.disciplineIds ?? [],
    employeeIds: search.employeeIds ?? [],
  };
}

/** Search-параметры адреса из состояния [filters]: только отличия от значений по умолчанию. */
export function searchOf(filters: GroupListFilters): GroupListSearch {
  return {
    ...(filters.q === "" ? {} : { q: filters.q }),
    ...(filters.disciplineIds.length === 0 ? {} : { disciplineIds: [...filters.disciplineIds] }),
    ...(filters.employeeIds.length === 0 ? {} : { employeeIds: [...filters.employeeIds] }),
  };
}

/** Активных фильтров (без поиска), для бейджа кнопки. */
export function activeFilterCount(filters: GroupListFilters): number {
  return (filters.disciplineIds.length > 0 ? 1 : 0) + (filters.employeeIds.length > 0 ? 1 : 0);
}

/** Запрос `/groups/list` из состояния [filters]. */
export function groupListRequest(filters: GroupListFilters): GroupListRequest {
  return {
    ...(filters.q === "" ? {} : { name: filters.q }),
    ...(filters.disciplineIds.length === 0 ? {} : { disciplineIds: filters.disciplineIds }),
    ...(filters.employeeIds.length === 0 ? {} : { employeeIds: filters.employeeIds }),
  };
}
