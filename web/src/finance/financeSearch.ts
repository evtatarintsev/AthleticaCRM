import { z } from "zod";
import {
  CashAccountIdSchema,
  CashArticleIdSchema,
  ClientIdSchema,
  EmployeeIdSchema,
  LedgerKindSchema,
  LocalDateSchema,
} from "@/api/generated/contracts";
import { todayLocalDate } from "@/lib/localDate";

/** Фильтры и вкладка финансов полностью восстанавливаются из адреса. */
export const FinanceSearchSchema = z.object({
  tab: z
    .enum(["operations", "cashFlow", "accounts", "articles"])
    .catch("operations")
    .default("operations"),
  from: LocalDateSchema.optional().catch(undefined),
  to: LocalDateSchema.optional().catch(undefined),
  cash: z.array(CashAccountIdSchema).catch([]).default([]),
  article: z.array(CashArticleIdSchema).catch([]).default([]),
  kind: LedgerKindSchema.optional().catch(undefined),
  client: ClientIdSchema.optional().catch(undefined),
  author: EmployeeIdSchema.optional().catch(undefined),
  includeReversed: z.boolean().catch(true).default(true),
});
export type FinanceSearch = z.output<typeof FinanceSearchSchema>;
export function financePeriod(search: FinanceSearch, timezone: string) {
  const today = todayLocalDate(timezone);
  return {
    from: search.from ?? LocalDateSchema.parse(`${today.slice(0, 7)}-01`),
    to: search.to ?? today,
  };
}
