import type { ApiClient, ApiResult, RequestOf, ResponseOf } from "@/api/client";

/** API авторизации — узкий интерфейс для экранов входа и регистрации, подменяется в тестах. */
export interface AuthApi {
  /** Филиалы, доступные по логину и паролю. */
  branches: (
    request: RequestOf<"auth/branches">,
  ) => Promise<ApiResult<ResponseOf<"auth/branches">>>;
  /** Вход в выбранный филиал. */
  login: (request: RequestOf<"auth/login">) => Promise<ApiResult<ResponseOf<"auth/login">>>;
  /** Регистрация организации. */
  signUp: (request: RequestOf<"auth/sign-up">) => Promise<ApiResult<ResponseOf<"auth/sign-up">>>;
}

/** API авторизации поверх клиента [api]. */
export function createAuthApi(api: ApiClient): AuthApi {
  return {
    branches: (request) => api.call("auth/branches", request),
    login: (request) => api.call("auth/login", request),
    signUp: (request) => api.call("auth/sign-up", request),
  };
}
