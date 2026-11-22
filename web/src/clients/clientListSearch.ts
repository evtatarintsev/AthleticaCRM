import { z } from "zod";
import type { ClientListRequest, DateRange, LocalDate } from "@/api/generated/contracts";
import { addDays } from "@/lib/localDate";

/** Колонка сортировки списка клиентов. */
export type ClientSortColumn = "name" | "balance";

/** Быстрый фильтр по дню рождения. */
export type ClientBirthdayFilter =
  "none" | "today" | "tomorrow" | "week" | "todayTomorrow" | "weekAhead";

/** Системный сохранённый вид списка клиентов. */
export type ClientViewId = "all" | "debt" | "no-group" | "archived";

/**
 * Полное состояние списка: фильтры, вид и сортировка. Значения по умолчанию
 * соответствуют «без фильтра» и сортировке по имени по возрастанию.
 */
export interface ClientListFilters {
  /** Поиск по имени. */
  readonly q: string;
  /** Фильтр по полу. */
  readonly gender: "all" | "male" | "female";
  /** Только клиенты с задолженностью. */
  readonly debt: boolean;
  /** Только клиенты без группы. */
  readonly noGroup: boolean;
  /** Фильтр по дню рождения. */
  readonly birthday: ClientBirthdayFilter;
  /** Режим архива: показывать архивных клиентов вместо активных. */
  readonly archived: boolean;
  /** Колонка сортировки. */
  readonly sort: ClientSortColumn | "nextBirthday";
  /** Направление сортировки. */
  readonly dir: "asc" | "desc";
}

/**
 * Search-параметры адреса `/clients`. В адресе хранятся только отличия от значений
 * по умолчанию, поэтому фильтры переживают перезагрузку, а адрес остаётся коротким.
 * Некорректное значение сбрасывается к умолчанию схемой (`catch`), а не роняет страницу.
 */
export const ClientListSearchSchema = z.object({
  q: z.string().optional().catch(undefined),
  gender: z.enum(["male", "female"]).optional().catch(undefined),
  debt: z.boolean().optional().catch(undefined),
  noGroup: z.boolean().optional().catch(undefined),
  birthday: z
    .enum(["today", "tomorrow", "week", "todayTomorrow", "weekAhead"])
    .optional()
    .catch(undefined),
  archived: z.boolean().optional().catch(undefined),
  sort: z.enum(["name", "balance", "nextBirthday"]).optional().catch(undefined),
  dir: z.enum(["asc", "desc"]).optional().catch(undefined),
});

/** Search-параметры адреса списка клиентов. */
export type ClientListSearch = z.output<typeof ClientListSearchSchema>;

/** Полное состояние списка из search-параметров [search] с подставленными значениями по умолчанию. */
export function clientListFilters(search: ClientListSearch): ClientListFilters {
  return normalizeBirthdaySort({
    q: search.q ?? "",
    gender: search.gender ?? "all",
    debt: search.debt ?? false,
    noGroup: search.noGroup ?? false,
    birthday: search.birthday ?? "none",
    archived: search.archived ?? false,
    sort: search.sort ?? "name",
    dir: search.dir ?? "asc",
  });
}

/** Ближайшие дни рождения требуют диапазон и всегда идут от ближайших к дальним. */
function normalizeBirthdaySort(filters: ClientListFilters): ClientListFilters {
  return filters.sort === "nextBirthday"
    ? { ...filters, sort: filters.birthday === "none" ? "name" : "nextBirthday", dir: "asc" }
    : filters;
}

/** Search-параметры адреса из состояния [filters]: только отличия от значений по умолчанию. */
export function searchOf(input: ClientListFilters): ClientListSearch {
  const filters = normalizeBirthdaySort(input);
  return {
    ...(filters.q === "" ? {} : { q: filters.q }),
    ...(filters.gender === "all" ? {} : { gender: filters.gender }),
    ...(filters.debt ? { debt: true } : {}),
    ...(filters.noGroup ? { noGroup: true } : {}),
    ...(filters.birthday === "none" ? {} : { birthday: filters.birthday }),
    ...(filters.archived ? { archived: true } : {}),
    ...(filters.sort === "name" ? {} : { sort: filters.sort }),
    ...(filters.dir === "asc" ? {} : { dir: filters.dir }),
  };
}

/** Количество активных фильтров (пол, долг, без группы, день рождения) для бейджа кнопки. */
export function activeFilterCount(filters: ClientListFilters): number {
  return [
    filters.gender !== "all",
    filters.debt,
    filters.noGroup,
    filters.birthday !== "none",
  ].filter(Boolean).length;
}

/**
 * Системный вид, которому соответствуют фильтры [filters], либо `null`, если фильтры
 * не совпадают ни с одним видом. Сортировка и поиск на вид не влияют.
 */
export function viewOf(filters: ClientListFilters): ClientViewId | null {
  const filtersOnly: Omit<ClientListFilters, "q" | "sort" | "dir"> = {
    gender: filters.gender,
    debt: filters.debt,
    noGroup: filters.noGroup,
    birthday: filters.birthday,
    archived: filters.archived,
  };
  const views: Readonly<Record<ClientViewId, Omit<ClientListFilters, "q" | "sort" | "dir">>> = {
    all: { gender: "all", debt: false, noGroup: false, birthday: "none", archived: false },
    debt: { gender: "all", debt: true, noGroup: false, birthday: "none", archived: false },
    "no-group": { gender: "all", debt: false, noGroup: true, birthday: "none", archived: false },
    archived: { gender: "all", debt: false, noGroup: false, birthday: "none", archived: true },
  };
  return CLIENT_VIEW_IDS.find((id) => isSameFilters(views[id], filtersOnly)) ?? null;
}

/**
 * Пустое состояние списка клиентов: клиентов нет вовсе, пуст выбранный системный вид
 * или ничего не нашлось по поиску и фильтрам.
 */
export type ClientListEmptyState = "no-clients" | "view-empty" | "no-results";

/**
 * Какое пустое состояние показать, если по фильтрам [filters] клиентов нет: без поиска
 * вид «Все» — `no-clients`, другой системный вид — `view-empty`, иначе — `no-results`.
 */
export function clientListEmptyState(filters: ClientListFilters): ClientListEmptyState {
  if (filters.q !== "") {
    return "no-results";
  }
  const view = viewOf(filters);
  if (view === null) {
    return "no-results";
  }
  return view === "all" ? "no-clients" : "view-empty";
}

/** Сброс фильтров [filters]: вид «Все» с пустым поиском и прежней сортировкой. */
export function clearedFilters(filters: ClientListFilters): ClientListFilters {
  return { ...clientListFilters({}), sort: filters.sort, dir: filters.dir };
}

/** Системные виды списка клиентов в устойчивом порядке. */
export const CLIENT_VIEW_IDS: readonly ClientViewId[] = ["all", "debt", "no-group", "archived"];

/** Истина, если все поля фильтров [a] и [b] совпадают. */
function isSameFilters(
  a: Omit<ClientListFilters, "q" | "sort" | "dir">,
  b: Omit<ClientListFilters, "q" | "sort" | "dir">,
): boolean {
  return (
    a.gender === b.gender &&
    a.debt === b.debt &&
    a.noGroup === b.noGroup &&
    a.birthday === b.birthday &&
    a.archived === b.archived
  );
}

/**
 * Диапазон дней рождения для фильтра [birthday] от сегодняшнего дня [today]:
 * сегодня, завтра или ближайшие 7 дней; `undefined` — фильтр не задан.
 */
export function birthdayRange(
  birthday: ClientBirthdayFilter,
  today: LocalDate,
): DateRange | undefined {
  switch (birthday) {
    case "none":
      return undefined;
    case "today":
      return { from: today, to: today };
    case "tomorrow":
      return { from: addDays(today, 1), to: addDays(today, 1) };
    case "week":
      return { from: today, to: addDays(today, 6) };
    case "todayTomorrow":
      return { from: today, to: addDays(today, 1) };
    case "weekAhead":
      return { from: today, to: addDays(today, 7) };
  }
}

/** Запрос `/clients/list` из состояния [filters] со страницей [limit]×[offset] от дня [today]. */
export function clientListRequest(
  input: ClientListFilters,
  today: LocalDate,
  limit: number,
  offset: number,
): ClientListRequest {
  const filters = normalizeBirthdaySort(input);
  const range = birthdayRange(filters.birthday, today);
  return {
    ...(filters.q === "" ? {} : { name: filters.q }),
    ...(filters.archived ? { archived: true } : {}),
    limit,
    offset,
    ...(filters.sort === "nextBirthday"
      ? {
          sort: [
            { field: "NEXT_BIRTHDAY", direction: "Asc" },
            { field: "NAME", direction: "Asc" },
          ] as const,
        }
      : {
          sortField: filters.sort === "name" ? ("NAME" as const) : ("BALANCE" as const),
          sortDirection: filters.dir === "asc" ? ("Asc" as const) : ("Desc" as const),
        }),
    ...(filters.gender === "all" ? {} : { gender: filters.gender === "male" ? "MALE" : "FEMALE" }),
    ...(filters.debt ? { hasDebt: true } : {}),
    ...(filters.noGroup ? { noGroup: true } : {}),
    ...(range === undefined ? {} : { birthday: range }),
  };
}
