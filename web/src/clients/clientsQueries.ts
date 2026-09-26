import { useInfiniteQuery, queryOptions } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import { LocalDateSchema, type BranchId, type LocalDate } from "@/api/generated/contracts";
import { unwrap } from "@/query/apiFailure";
import { apiQuery } from "@/query/queries";
import { clientListRequest, type ClientListFilters } from "./clientListSearch";

/** Размер страницы списка клиентов при серверной пагинации. */
export const CLIENTS_PAGE_SIZE = 50;

/** Ключ кэша настроек отображения; настройки пользовательские, от филиала не зависят. */
export const displaySettingsKey = ["session", "display-settings"] as const;

/** Настройки отображения текущего пользователя. */
export function displaySettingsQuery(api: ApiClient) {
  return queryOptions({
    queryKey: displaySettingsKey,
    queryFn: async () => unwrap(await api.call("display-settings")),
  });
}

/** Определения дополнительных полей клиентов филиала [branchId]. */
export function clientCustomFieldsQuery(api: ApiClient, branchId: BranchId) {
  return apiQuery(api, branchId, "custom-fields/list", { entityType: "CLIENT" });
}

/**
 * Страницы списка клиентов для состояния [filters] в филиале [branchId]:
 * серверная пагинация «показать ещё», каждая страница — отдельный запрос.
 * Фильтры входят в ключ: смена фильтра начинает загрузку заново.
 */
export function useClientPages(api: ApiClient, branchId: BranchId, filters: ClientListFilters) {
  return useInfiniteQuery({
    queryKey: ["api", branchId, "clients/list", filters] as const,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.call(
          "clients/list",
          clientListRequest(filters, todayLocalDate(), CLIENTS_PAGE_SIZE, pageParam),
        ),
      ),
    getNextPageParam: (last, pages) => {
      const loaded = pages.reduce((count, page) => count + page.clients.length, 0);
      return loaded < last.total ? loaded : undefined;
    },
  });
}

/** Сегодняшний день в часовом поясе браузера. */
export function todayLocalDate(): LocalDate {
  return LocalDateSchema.parse(
    new Intl.DateTimeFormat("en-CA", { year: "numeric", month: "2-digit", day: "2-digit" }).format(
      new Date(),
    ),
  );
}
