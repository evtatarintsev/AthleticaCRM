import type { QueryClient } from "@tanstack/react-query";
import type { ApiClient, ApiResult } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { sessionQuery } from "@/query/queries";

/**
 * Переключает пользователя в филиал [branchId]. Сервер меняет филиал в cookie сессии,
 * после чего сессия перечитывается, а данные других филиалов удаляются из кэша [queryClient]:
 * открытые страницы запрашивают свои данные заново по ключу с новым филиалом.
 */
export async function switchBranch(
  api: ApiClient,
  queryClient: QueryClient,
  branchId: BranchId,
): Promise<ApiResult<undefined>> {
  const result = await api.call("auth/switch-branch", { branchId });
  if (!result.ok) {
    return result;
  }
  await queryClient.refetchQueries({ queryKey: sessionQuery(api).queryKey });
  queryClient.removeQueries({
    predicate: (query) => query.queryKey[0] === "api" && query.queryKey[1] !== branchId,
  });
  return { ok: true, value: undefined };
}
