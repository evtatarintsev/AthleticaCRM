import { z } from "zod";
import { EmployeeIdSchema } from "@/api/generated/contracts";

/**
 * Search-параметры списка сотрудников — открытые панели: `employee` — карточка сотрудника,
 * `edit` — панель его редактирования поверх карточки, `create` — создание сотрудника.
 * Если указаны и `employee`, и `create`, открывается карточка.
 */
export const EmployeeListSearchSchema = z.object({
  employee: EmployeeIdSchema.optional().catch(undefined),
  edit: z.literal(true).optional().catch(undefined),
  create: z.literal(true).optional().catch(undefined),
});

/** Search-параметры списка сотрудников, проверенные [EmployeeListSearchSchema]. */
export type EmployeeListSearch = z.output<typeof EmployeeListSearchSchema>;
