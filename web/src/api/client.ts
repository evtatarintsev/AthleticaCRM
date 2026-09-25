import type { z } from "zod";
import {
  AuthBranchesResponseSchema,
  ErrorResponseSchema,
  LoginResponseSchema,
  type AuthBranchesRequest,
  type AuthBranchesResponse,
  type LoginRequest,
  type LoginResponse,
  type SignUpRequest,
} from "./schemas";

/** Ошибка вызова API — аналог `ApiClientError` из KMP-клиента. */
export type ApiError =
  | { kind: "validation"; code: string; message: string }
  | { kind: "unauthenticated" }
  | { kind: "unavailable" };

/** Результат вызова API: успех или типизированная ошибка, без исключений. */
export type ApiResult<T> = { ok: true; value: T } | { ok: false; error: ApiError };

/** Тело ответа как JSON; `undefined`, если тело не разбирается. */
async function jsonBody(response: Response): Promise<unknown> {
  try {
    const body: unknown = await response.json();
    return body;
  } catch {
    return undefined;
  }
}

/**
 * Отправляет POST с JSON-телом [body] на `/api{path}` и декодирует ответ схемой [schema].
 * Сессия живёт в HttpOnly-cookie того же домена, поэтому токены вручную не передаются.
 * Ответ, не совпадающий со схемой, считается недоступностью сервиса.
 */
async function post<Res>(
  path: string,
  body: unknown,
  schema: z.ZodType<Res>,
): Promise<ApiResult<Res>> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    return { ok: false, error: { kind: "unavailable" } };
  }
  if (response.ok) {
    const decoded = schema.safeParse(await jsonBody(response));
    if (!decoded.success) {
      console.error(`Ответ ${path} не совпадает с контрактом`, decoded.error);
      return { ok: false, error: { kind: "unavailable" } };
    }
    return { ok: true, value: decoded.data };
  }
  if (response.status === 401 || response.status === 403) {
    return { ok: false, error: { kind: "unauthenticated" } };
  }
  if (response.status >= 400 && response.status < 500) {
    const payload = ErrorResponseSchema.safeParse(await jsonBody(response));
    return {
      ok: false,
      error: {
        kind: "validation",
        code: payload.success ? payload.data.code : "",
        message: payload.success ? payload.data.message : "",
      },
    };
  }
  return { ok: false, error: { kind: "unavailable" } };
}

/** Интерфейс API авторизации — для подмены в тестах. */
export interface AuthApi {
  branches: (request: AuthBranchesRequest) => Promise<ApiResult<AuthBranchesResponse>>;
  login: (request: LoginRequest) => Promise<ApiResult<LoginResponse>>;
  signUp: (request: SignUpRequest) => Promise<ApiResult<LoginResponse>>;
}

/** Методы API авторизации. */
export const authApi: AuthApi = {
  branches: (request) => post("/auth/branches", request, AuthBranchesResponseSchema),
  login: (request) => post("/auth/login", request, LoginResponseSchema),
  signUp: (request) => post("/auth/sign-up", request, LoginResponseSchema),
};
