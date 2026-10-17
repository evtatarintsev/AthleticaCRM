import { useInfiniteQuery, useQuery, type QueryClient } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { BranchId, InboxFilterSchema } from "@/api/generated/contracts";
import { unwrap } from "@/query/apiFailure";
import { apiQuery } from "@/query/queries";

/** Размер страницы списка «Входящих». */
export const INBOX_PAGE_SIZE = 50;

/** Как часто перечитывать список «Входящих» и открытую беседу. */
export const INBOX_REFRESH_MS = 15 * 1000;

/** Как часто перечитывать счётчик «Входящих» в навигации. */
const COUNTER_REFRESH_MS = 30 * 1000;

/**
 * Страницы списка «Входящих» с фильтром [filter] в филиале [branchId]: «показать ещё»
 * и периодическое обновление, пока вкладка видима. Фильтр входит в ключ.
 */
export function useInboxPages(api: ApiClient, branchId: BranchId, filter: InboxFilterSchema) {
  return useInfiniteQuery({
    queryKey: ["api", branchId, "messaging/inbox/list", filter] as const,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.call("messaging/inbox/list", {
          filter,
          limit: INBOX_PAGE_SIZE,
          offset: pageParam,
        }),
      ),
    getNextPageParam: (last, pages) => {
      const loaded = pages.reduce((count, page) => count + page.items.length, 0);
      return loaded < last.total ? loaded : undefined;
    },
    refetchInterval: INBOX_REFRESH_MS,
  });
}

/** Число записей «Входящих», требующих внимания; `0`, пока не загружено или при ошибке. */
export function useInboxCounter(api: ApiClient, branchId: BranchId): number {
  const { data } = useQuery({
    ...apiQuery(api, branchId, "messaging/inbox/counter"),
    refetchInterval: COUNTER_REFRESH_MS,
  });
  return data?.count ?? 0;
}

/** Помечает устаревшими список и счётчик «Входящих» филиала [branchId]. */
export async function invalidateInbox(queryClient: QueryClient, branchId: BranchId): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["api", branchId, "messaging/inbox/list"] }),
    queryClient.invalidateQueries({ queryKey: ["api", branchId, "messaging/inbox/counter"] }),
  ]);
}
