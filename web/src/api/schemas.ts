/**
 * Контракты API авторизации.
 *
 * Зеркало Kotlin-схем из `shared/src/commonMain/kotlin/org/athletica/crm/api/schemas/`.
 * Пока поддерживается вручную; следующий шаг — генерировать этот файл из `shared`.
 */
import { z } from "zod";

/** Идентификатор филиала (UUID). */
export type BranchId = string;

/** Код валюты ISO 4217 — зеркало `core.money.Currency`. */
export type Currency = "RUB" | "USD" | "EUR" | "KZT" | "BYN" | "UAH";

/** Валюты в порядке показа и их символы. */
export const CURRENCIES: readonly { code: Currency; symbol: string }[] = [
  { code: "RUB", symbol: "₽" },
  { code: "USD", symbol: "$" },
  { code: "EUR", symbol: "€" },
  { code: "KZT", symbol: "₸" },
  { code: "BYN", symbol: "Br" },
  { code: "UAH", symbol: "₴" },
];

/** `api.schemas.auth.AuthBranchesRequest`. */
export interface AuthBranchesRequest {
  username: string;
  password: string;
}

/** `api.schemas.branches.BranchDetailResponse`. */
export const BranchDetailResponseSchema = z.object({ id: z.string(), name: z.string() });

/** `api.schemas.branches.BranchDetailResponse`. */
export type BranchDetailResponse = z.output<typeof BranchDetailResponseSchema>;

/** `api.schemas.auth.AuthBranchesResponse`. */
export const AuthBranchesResponseSchema = z.object({
  branches: z.array(BranchDetailResponseSchema),
});

/** `api.schemas.auth.AuthBranchesResponse`. */
export type AuthBranchesResponse = z.output<typeof AuthBranchesResponseSchema>;

/** `api.schemas.auth.LoginRequest`. */
export interface LoginRequest {
  username: string;
  password: string;
  branchId: BranchId;
}

/** `api.schemas.auth.LoginResponse`. Токены дублируются в HttpOnly-cookie. */
export const LoginResponseSchema = z.object({ accessToken: z.string(), refreshToken: z.string() });

/** `api.schemas.auth.LoginResponse`. */
export type LoginResponse = z.output<typeof LoginResponseSchema>;

/** `api.schemas.auth.SignUpRequest`. */
export interface SignUpRequest {
  companyName: string;
  userName: string;
  login: string;
  password: string;
  timezone: string;
  currency: Currency;
}

/** `api.schemas.ErrorResponse`. */
export const ErrorResponseSchema = z.object({ code: z.string(), message: z.string() });
