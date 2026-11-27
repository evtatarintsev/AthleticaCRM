import { z } from "zod";
import {
  BranchIdSchema,
  LocalDateSchema,
  ReportCompareModeSchema,
  ReportPeriodKindSchema,
  type ReportFilter,
} from "@/api/generated/contracts";

/**
 * Фильтры раздела «Отчёты» в search-параметрах `/reports` и `/reports/$reportId`.
 * Неверное значение заменяется значением по умолчанию («Этот месяц», карточки); свой
 * период без дат или с началом позже конца не применяется — остаётся «Этот месяц».
 */
export const ReportSearchSchema = z
  .object({
    period: ReportPeriodKindSchema.catch("month").default("month"),
    from: LocalDateSchema.optional().catch(undefined),
    to: LocalDateSchema.optional().catch(undefined),
    compare: ReportCompareModeSchema.catch("days-before").default("days-before"),
    branch: BranchIdSchema.optional().catch(undefined),
    view: z.enum(["cards", "table"]).catch("cards").default("cards"),
  })
  .transform((search) => {
    if (search.period !== "custom") {
      return { ...search, from: undefined, to: undefined };
    }
    if (search.from === undefined || search.to === undefined || search.from > search.to) {
      return { ...search, period: "month" as const, from: undefined, to: undefined };
    }
    return search;
  });

/** Разобранные фильтры раздела «Отчёты». */
export type ReportSearch = z.output<typeof ReportSearchSchema>;

/** Фильтры по умолчанию: «Этот месяц», все филиалы, карточки. */
export const defaultReportSearch: ReportSearch = ReportSearchSchema.parse({});

/** Запрос отчётов по фильтрам адреса [search]. */
export function reportFilter(search: ReportSearch): ReportFilter {
  return {
    period: search.period,
    from: search.from ?? null,
    to: search.to ?? null,
    compare: search.compare,
    branchId: search.branch ?? null,
  };
}
