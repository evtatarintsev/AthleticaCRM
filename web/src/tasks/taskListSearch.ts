import { z } from "zod";
import { TaskStatusSchema, type TaskListRequest, type TaskStatus } from "@/api/generated/contracts";

/** Колонка сортировки списка задач; сортировка применяется на клиенте — сервер её не поддерживает. */
export type TaskSortColumn = "title" | "status" | "assignee" | "dueDate";

/** Системный вид списка задач. */
export type TaskViewId = "all" | "mine";

/** Полное состояние списка задач: поиск, фильтры и сортировка. */
export interface TaskListFilters {
  /** Поиск по заголовку и описанию. */
  readonly q: string;
  /** Только задачи, где текущий сотрудник — исполнитель или создатель. */
  readonly onlyMine: boolean;
  /** Фильтр по статусам; пустой список — все статусы. */
  readonly statuses: readonly TaskStatus[];
  /** Колонка сортировки. */
  readonly sort: TaskSortColumn;
  /** Направление сортировки. */
  readonly dir: "asc" | "desc";
}

/**
 * Search-параметры адреса `/tasks`. В адресе хранятся только отличия от значений
 * по умолчанию, поэтому фильтры переживают перезагрузку. Некорректное значение
 * сбрасывается к умолчанию схемой (`catch`), а не роняет страницу.
 */
export const TaskListSearchSchema = z.object({
  q: z.string().optional().catch(undefined),
  onlyMine: z.boolean().optional().catch(undefined),
  statuses: z.array(TaskStatusSchema).optional().catch(undefined),
  sort: z.enum(["title", "status", "assignee", "dueDate"]).optional().catch(undefined),
  dir: z.enum(["asc", "desc"]).optional().catch(undefined),
});

/** Search-параметры адреса списка задач. */
export type TaskListSearch = z.output<typeof TaskListSearchSchema>;

/** Полное состояние списка из search-параметров [search] с подставленными значениями по умолчанию. */
export function taskListFilters(search: TaskListSearch): TaskListFilters {
  return {
    q: search.q ?? "",
    onlyMine: search.onlyMine ?? false,
    statuses: search.statuses ?? [],
    sort: search.sort ?? "dueDate",
    dir: search.dir ?? "asc",
  };
}

/** Search-параметры адреса из состояния [filters]: только отличия от значений по умолчанию. */
export function searchOf(filters: TaskListFilters): TaskListSearch {
  return {
    ...(filters.q === "" ? {} : { q: filters.q }),
    ...(filters.onlyMine ? { onlyMine: true } : {}),
    ...(filters.statuses.length === 0 ? {} : { statuses: [...filters.statuses] }),
    ...(filters.sort === "dueDate" ? {} : { sort: filters.sort }),
    ...(filters.dir === "asc" ? {} : { dir: filters.dir }),
  };
}

/** Количество активных фильтров (только мои, статусы) для бейджа кнопки фильтров. */
export function activeFilterCount(filters: TaskListFilters): number {
  return [filters.onlyMine, filters.statuses.length > 0].filter(Boolean).length;
}

/**
 * Системный вид, которому соответствуют фильтры [filters], либо `null`, если фильтры
 * не совпадают ни с одним видом. Сортировка, поиск и статусы на вид не влияют.
 */
export function viewOf(filters: TaskListFilters): TaskViewId | null {
  if (filters.statuses.length > 0) {
    return null;
  }
  return filters.onlyMine ? "mine" : "all";
}

/** Системные виды списка задач в устойчивом порядке. */
export const TASK_VIEW_IDS: readonly TaskViewId[] = ["all", "mine"];

/** Запрос `/tasks/list` из состояния [filters]: сервер фильтрует по `onlyMine`/статусам/тексту. */
export function taskListRequest(filters: TaskListFilters): TaskListRequest {
  return {
    ...(filters.onlyMine ? { onlyMine: true } : {}),
    ...(filters.statuses.length === 0 ? {} : { statuses: filters.statuses }),
    ...(filters.q === "" ? {} : { searchText: filters.q }),
  };
}
