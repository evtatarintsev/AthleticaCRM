import type {
  AuthBranchesRequest,
  AuthBranchesResponse,
  ErrorResponse,
  LoginRequest,
  LoginResponse,
  SignUpRequest,
} from "./schemas";

/** Ошибка вызова API — аналог `ApiClientError` из KMP-клиента. */
export type ApiError =
  | { kind: "validation"; code: string; message: string }
  | { kind: "unauthenticated" }
  | { kind: "unavailable" };

/** Результат вызова API: успех или типизированная ошибка, без исключений. */
export type ApiResult<T> = { ok: true; value: T } | { ok: false; error: ApiError };

/**
 * Отправляет POST с JSON-телом на `/api{path}`.
 * Сессия живёт в HttpOnly-cookie того же домена, поэтому токены вручную не передаются.
 */
async function post<Req, Res>(path: string, body: Req): Promise<ApiResult<Res>> {
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
    return { ok: true, value: (await response.json()) as Res };
  }
  if (response.status === 401 || response.status === 403) {
    return { ok: false, error: { kind: "unauthenticated" } };
  }
  if (response.status >= 400 && response.status < 500) {
    const payload = (await response.json().catch(() => null)) as ErrorResponse | null;
    return {
      ok: false,
      error: {
        kind: "validation",
        code: payload?.code ?? "",
        message: payload?.message ?? "",
      },
    };
  }
  return { ok: false, error: { kind: "unavailable" } };
}

/** Методы API авторизации. */
export const authApi = {
  branches: (request: AuthBranchesRequest) =>
    post<AuthBranchesRequest, AuthBranchesResponse>("/auth/branches", request),
  login: (request: LoginRequest) => post<LoginRequest, LoginResponse>("/auth/login", request),
  signUp: (request: SignUpRequest) => post<SignUpRequest, LoginResponse>("/auth/sign-up", request),
};

/** Интерфейс API авторизации — для подмены в тестах. */
export type AuthApi = typeof authApi;
