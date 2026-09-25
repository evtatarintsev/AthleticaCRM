/**
 * Контракты API авторизации.
 *
 * Зеркало Kotlin-схем из `shared/src/commonMain/kotlin/org/athletica/crm/api/schemas/`.
 * Пока поддерживается вручную; следующий шаг — генерировать этот файл из `shared`.
 */

/** Идентификатор филиала (UUID). */
export type BranchId = string;

/** Код валюты ISO 4217 — зеркало `core.money.Currency`. */
export type Currency = "RUB" | "USD" | "EUR" | "KZT" | "BYN" | "UAH";

/** Валюты в порядке показа и их символы. */
export const CURRENCIES: ReadonlyArray<{ code: Currency; symbol: string }> = [
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
export interface BranchDetailResponse {
  id: BranchId;
  name: string;
}

/** `api.schemas.auth.AuthBranchesResponse`. */
export interface AuthBranchesResponse {
  branches: BranchDetailResponse[];
}

/** `api.schemas.auth.LoginRequest`. */
export interface LoginRequest {
  username: string;
  password: string;
  branchId: BranchId;
}

/** `api.schemas.auth.LoginResponse`. Токены дублируются в HttpOnly-cookie. */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

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
export interface ErrorResponse {
  code: string;
  message: string;
  fields?: { name: string; error: string }[] | null;
}
