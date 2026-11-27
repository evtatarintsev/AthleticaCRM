import type { ApiClient } from "@/api/client";
import type { BranchId, ReportFilter } from "@/api/generated/contracts";
import { apiQuery } from "@/query/queries";

/** Таблица показателей по фильтру [filter]; её же даты периода показывает заголовок раздела. */
export function reportsTableQuery(api: ApiClient, branchId: BranchId, filter: ReportFilter) {
  return apiQuery(api, branchId, "reports/table", filter);
}
