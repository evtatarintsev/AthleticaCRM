// Сгенерировано задачей :server:generateWebContracts из маршрутов сервера и схем shared.
// Не редактировать вручную: после изменения схем выполните `npm run contracts`.

import { z } from "zod";

export const ClientAppLoginRequestSchema = z
  .object({
    phone: z.string(),
    code: z.string(),
    acceptedTermsVersion: z.string().nullable().optional(),
  })
  .readonly();
export type ClientAppLoginRequest = z.output<typeof ClientAppLoginRequestSchema>;

export const ClientAppRequestCodeRequestSchema = z
  .object({
    phone: z.string(),
    viaSms: z.boolean().optional(),
  })
  .readonly();
export type ClientAppRequestCodeRequest = z.output<typeof ClientAppRequestCodeRequestSchema>;

export const ClientAppRequestCodeResponseSchema = z
  .object({
    sent: z.boolean(),
    retryAfterSeconds: z.int(),
    termsVersion: z.string(),
  })
  .readonly();
export type ClientAppRequestCodeResponse = z.output<typeof ClientAppRequestCodeResponseSchema>;

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
  "client-app/auth/login": {
    method: "POST",
    in: "body",
    request: ClientAppLoginRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "client-app/auth/logout": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
  "client-app/auth/refresh": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
  "client-app/auth/request-code": {
    method: "POST",
    in: "body",
    request: ClientAppRequestCodeRequestSchema,
    out: "json",
    response: ClientAppRequestCodeResponseSchema,
  },
} as const;
