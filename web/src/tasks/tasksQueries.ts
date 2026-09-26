import { queryOptions } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { unwrap } from "@/query/apiFailure";
import { taskListRequest, type TaskListFilters } from "./taskListSearch";

/**
 * Список задач для состояния [filters] в филиале [branchId]. Сервер отдаёт список
 * целиком без пагинации (как и KMP-клиент); фильтры входят в ключ запроса.
 */
export function tasksQuery(api: ApiClient, branchId: BranchId, filters: TaskListFilters) {
  return queryOptions({
    queryKey: ["api", branchId, "tasks/list", filters] as const,
    queryFn: async () => unwrap(await api.call("tasks/list", taskListRequest(filters))),
  });
}
