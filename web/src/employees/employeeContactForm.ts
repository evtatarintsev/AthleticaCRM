import { z } from "zod";
import type { I18n } from "@/i18n/context";

/** Значения контактных полей формы сотрудника: общие для создания и редактирования. */
export interface EmployeeContactValues {
  readonly name: string;
  readonly phoneNo: string;
  readonly email: string;
}

/** Схема контактных полей с сообщениями на языке [t]: имя и email обязательны. */
export function employeeContactSchema(t: I18n["t"]) {
  return z.object({
    name: z.string().trim().min(1, t("error.required")),
    phoneNo: z.string(),
    email: z
      .string()
      .trim()
      .pipe(z.email(t("error.invalidEmail"))),
  });
}
