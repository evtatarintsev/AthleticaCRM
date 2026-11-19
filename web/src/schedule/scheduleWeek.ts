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
import { addDays, mondayOf } from "@/lib/localDate";

/** Режим отображения расписания. */
export const ScheduleViewSchema = z.enum(["day", "week"]);

/** Режим отображения расписания: день или неделя. */
export type ScheduleView = z.output<typeof ScheduleViewSchema>;

/**
 * Search-параметры адреса `/schedule`: режим, дата отображаемого периода и фильтры.
 * Отсутствие режима — режим по ширине экрана, отсутствие даты — период с сегодняшним днём.
 */
export const ScheduleSearchSchema = z.object({
  view: ScheduleViewSchema.optional().catch(undefined),
  date: LocalDateSchema.optional().catch(undefined),
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

/** Режим и дата отображаемого периода — то, что вместе с фильтрами пишется в адрес. */
export interface SchedulePeriod {
  readonly view?: ScheduleView | undefined;
  readonly date?: LocalDate | undefined;
}

/** Выбранная дата из search-параметров [search]; без даты — сегодняшний день [today] организации. */
export function selectedDateOf(search: ScheduleSearch, today: LocalDate): LocalDate {
  return search.date ?? today;
}

/** Режим из адреса [search]; без него — «Неделя» на широком экране [wide], иначе «День». */
export function scheduleViewOf(search: ScheduleSearch, wide: boolean): ScheduleView {
  return search.view ?? (wide ? "week" : "day");
}

/** Дата периода [date] режима [view], сдвинутая на [step] дней или недель. */
export function shiftedDate(view: ScheduleView, date: LocalDate, step: number): LocalDate {
  return view === "day" ? addDays(date, step) : addDays(mondayOf(date), step * 7);
}

/**
 * Дата при смене режима [from] → [to] для выбранной даты [date]: неделя → день открывает
 * сегодняшний день [today], если он в неделе, иначе понедельник; день → неделя — неделю этого дня.
 */
export function dateForView(
  from: ScheduleView,
  to: ScheduleView,
  date: LocalDate,
  today: LocalDate,
): LocalDate {
  if (from === "week" && to === "day") {
    return mondayOf(today) === mondayOf(date) ? today : mondayOf(date);
  }
  return date;
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

/**
 * Search-параметры периода [period] и фильтров [filters]. Выбранная дата сохраняется всегда,
 * даже сегодняшняя: ссылка на день должна открывать тот же день и завтра. Пустые фильтры опускаются.
 */
export function scheduleSearchOf(period: SchedulePeriod, filters: ScheduleFilters): ScheduleSearch {
  return {
    ...(period.view === undefined ? {} : { view: period.view }),
    ...(period.date === undefined ? {} : { date: period.date }),
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
