// Сгенерировано задачей :server:generateWebContracts из маршрутов сервера и схем shared.
// Не редактировать вручную: после изменения схем выполните `npm run contracts`.

import { z } from "zod";

export const LocalDateSchema = z.iso.date().brand<"LocalDate">();
export type LocalDate = z.output<typeof LocalDateSchema>;

export const LedgerTransactionIdSchema = z.uuid().brand<"LedgerTransactionId">();
export type LedgerTransactionId = z.output<typeof LedgerTransactionIdSchema>;

export const LedgerKindSchema = z.enum(["SALE", "SALE_RETURN", "PAYMENT", "PAYOUT", "ADJUSTMENT", "INCOME", "EXPENSE", "TRANSFER", "OPENING", "REVERSAL"]);
export type LedgerKind = z.output<typeof LedgerKindSchema>;

export const CurrencySchema = z.enum(["RUB", "USD", "EUR", "KZT", "BYN", "UAH"]);
export type Currency = z.output<typeof CurrencySchema>;

export const MoneySchema = z
  .object({
    minorUnits: z.int(),
    currency: CurrencySchema,
  })
  .readonly();
export type Money = z.output<typeof MoneySchema>;

export const ClientAppBalanceEntrySchemaSchema = z
  .object({
    id: LedgerTransactionIdSchema,
    occurredOn: LocalDateSchema,
    kind: LedgerKindSchema,
    amount: MoneySchema,
  })
  .readonly();
export type ClientAppBalanceEntrySchema = z.output<typeof ClientAppBalanceEntrySchemaSchema>;

export const ClientAppBalanceResponseSchema = z
  .object({
    balance: MoneySchema,
    unpaidSessions: z.int32(),
    entries: z.array(ClientAppBalanceEntrySchemaSchema).readonly(),
  })
  .readonly();
export type ClientAppBalanceResponse = z.output<typeof ClientAppBalanceResponseSchema>;

export const ClientAppHomeResponseSchema = z
  .object({
    clientName: z.string(),
    orgName: z.string(),
  })
  .readonly();
export type ClientAppHomeResponse = z.output<typeof ClientAppHomeResponseSchema>;

export const OrgIdSchema = z.uuid().brand<"OrgId">();
export type OrgId = z.output<typeof OrgIdSchema>;

export const ClientAppInvitationRequestSchema = z
  .object({
    orgId: OrgIdSchema,
  })
  .readonly();
export type ClientAppInvitationRequest = z.output<typeof ClientAppInvitationRequestSchema>;

export const InstantSchema = z.iso.datetime({ offset: true }).brand<"Instant">();
export type Instant = z.output<typeof InstantSchema>;

export const ClientAppInvitationSchemaSchema = z
  .object({
    orgId: OrgIdSchema,
    orgName: z.string(),
    invitedAt: InstantSchema,
    clientCount: z.int32(),
  })
  .readonly();
export type ClientAppInvitationSchema = z.output<typeof ClientAppInvitationSchemaSchema>;

export const ClientAppInvitationsResponseSchema = z
  .object({
    invitations: z.array(ClientAppInvitationSchemaSchema).readonly(),
  })
  .readonly();
export type ClientAppInvitationsResponse = z.output<typeof ClientAppInvitationsResponseSchema>;

export const ClientAppLoginRequestSchema = z
  .object({
    phone: z.string(),
    code: z.string(),
    acceptedTermsVersion: z.string().nullable().optional(),
  })
  .readonly();
export type ClientAppLoginRequest = z.output<typeof ClientAppLoginRequestSchema>;

export const ClientIdSchema = z.uuid().brand<"ClientId">();
export type ClientId = z.output<typeof ClientIdSchema>;

export const ClientAppProfileRequestSchema = z
  .object({
    orgId: OrgIdSchema,
    clientId: ClientIdSchema,
  })
  .readonly();
export type ClientAppProfileRequest = z.output<typeof ClientAppProfileRequestSchema>;

export const ClientAppProfileSchemaSchema = z
  .object({
    orgId: OrgIdSchema,
    orgName: z.string(),
    clientId: ClientIdSchema,
    clientName: z.string(),
  })
  .readonly();
export type ClientAppProfileSchema = z.output<typeof ClientAppProfileSchemaSchema>;

export const ClientAppProfilesResponseSchema = z
  .object({
    profiles: z.array(ClientAppProfileSchemaSchema).readonly(),
    newInvitations: z.int32(),
  })
  .readonly();
export type ClientAppProfilesResponse = z.output<typeof ClientAppProfilesResponseSchema>;

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

export const ClientAppSessionIdSchema = z.uuid().brand<"ClientAppSessionId">();
export type ClientAppSessionId = z.output<typeof ClientAppSessionIdSchema>;

export const ClientAppSessionSchemaSchema = z
  .object({
    id: ClientAppSessionIdSchema,
    name: z.string().nullable(),
    createdAt: InstantSchema,
    lastSeenAt: InstantSchema,
    current: z.boolean(),
  })
  .readonly();
export type ClientAppSessionSchema = z.output<typeof ClientAppSessionSchemaSchema>;

export const ClientAppSessionsResponseSchema = z
  .object({
    sessions: z.array(ClientAppSessionSchemaSchema).readonly(),
  })
  .readonly();
export type ClientAppSessionsResponse = z.output<typeof ClientAppSessionsResponseSchema>;

export const ClientAppTerminateSessionRequestSchema = z
  .object({
    sessionId: ClientAppSessionIdSchema,
  })
  .readonly();
export type ClientAppTerminateSessionRequest = z.output<typeof ClientAppTerminateSessionRequestSchema>;

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
  "client-app/account/delete": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
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
  "client-app/balance": {
    method: "GET",
    in: "query",
    request: ClientAppProfileRequestSchema,
    out: "json",
    response: ClientAppBalanceResponseSchema,
  },
  "client-app/connect": {
    method: "POST",
    in: "body",
    request: ClientAppInvitationRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "client-app/home": {
    method: "GET",
    in: "query",
    request: ClientAppProfileRequestSchema,
    out: "json",
    response: ClientAppHomeResponseSchema,
  },
  "client-app/invitations": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: ClientAppInvitationsResponseSchema,
  },
  "client-app/not-me": {
    method: "POST",
    in: "body",
    request: ClientAppInvitationRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "client-app/profiles": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: ClientAppProfilesResponseSchema,
  },
  "client-app/sessions": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: ClientAppSessionsResponseSchema,
  },
  "client-app/sessions/terminate": {
    method: "POST",
    in: "body",
    request: ClientAppTerminateSessionRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
} as const;
