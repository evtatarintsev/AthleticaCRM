// Сгенерировано задачей :server:generateWebContracts из маршрутов сервера и схем shared.
// Не редактировать вручную: после изменения схем выполните `npm run contracts`.

import { z } from "zod";

export const FieldErrorSchema = z
  .object({
    name: z.string(),
    error: z.string(),
  })
  .readonly();
export type FieldError = z.output<typeof FieldErrorSchema>;

export const ErrorResponseSchema = z
  .object({
    code: z.string(),
    message: z.string(),
    fields: z.array(FieldErrorSchema).readonly().nullable(),
  })
  .readonly();
export type ErrorResponse = z.output<typeof ErrorResponseSchema>;

/** Все эндпоинты API: путь без `/api/` → метод, формат запроса и ответа. */
export const endpoints = {
} as const;
