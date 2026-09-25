import { QueryClient, queryOptions } from "@tanstack/react-query";
import type { ApiClient, CallArgs, EndpointPath } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { apiErrorOf, unwrap } from "./apiFailure";

/** Сколько раз повторять запрос, если сервис недоступен. */
const UNAVAILABLE_RETRIES = 2;

/**
 * Клиент кэша запросов. Повторяются только запросы, упавшие из-за недоступности сервиса:
 * бизнес-ошибка, нарушение контракта и отсутствие сессии от повтора не исчезнут.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failures, error) =>
          apiErrorOf(error).kind === "unavailable" && failures < UNAVAILABLE_RETRIES,
      },
      mutations: { retry: false },
    },
  });
}

/**
 * Запрос к эндпоинту [path] с аргументами [args] в филиале [branchId].
 * Филиал входит в ключ: после смены филиала тот же эндпоинт запрашивается заново,
 * а данные прежнего филиала не показываются даже на время загрузки.
 */
export function apiQuery<P extends EndpointPath>(
  api: ApiClient,
  branchId: BranchId,
  path: P,
  ...args: CallArgs<P>
) {
  return queryOptions({
    queryKey: ["api", branchId, path, ...args] as const,
    queryFn: async () => unwrap(await api.call(path, ...args)),
  });
}

/** Текущий пользователь, его организация и филиал; от филиала не зависит — он его определяет. */
export function sessionQuery(api: ApiClient) {
  return queryOptions({
    queryKey: ["session", "auth/me"] as const,
    queryFn: async () => unwrap(await api.call("auth/me")),
    staleTime: 5 * 60 * 1000,
  });
}
