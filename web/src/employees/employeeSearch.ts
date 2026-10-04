import { z } from "zod";

/** Search-параметры списка сотрудников: `create` — открыта панель создания сотрудника. */
export const EmployeeListSearchSchema = z.object({
  create: z.literal(true).optional().catch(undefined),
});

/** Search-параметры списка сотрудников, проверенные [EmployeeListSearchSchema]. */
export type EmployeeListSearch = z.output<typeof EmployeeListSearchSchema>;

/** Search-параметры карточки сотрудника: `edit` — открыта панель редактирования. */
export const EmployeeDetailSearchSchema = z.object({
  edit: z.literal(true).optional().catch(undefined),
});

/** Search-параметры карточки сотрудника, проверенные [EmployeeDetailSearchSchema]. */
export type EmployeeDetailSearch = z.output<typeof EmployeeDetailSearchSchema>;
