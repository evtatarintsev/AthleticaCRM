import type { ApiError } from "@/api/client";
import type { BranchDetailResponse, BranchId } from "@/api/generated/contracts";
import type { AuthApi } from "./authApi";

/** Учётные данные, введённые на экране входа. */
export interface Credentials {
  readonly username: string;
  readonly password: string;
}

/** Причина неудачного входа; текст подбирает экран на языке интерфейса. */
export type LoginFailure =
  | { readonly kind: "invalidCredentials" }
  | { readonly kind: "noBranches" }
  | { readonly kind: "unavailable" }
  /** Сервер отказал с сообщением [message] на языке запроса. */
  | { readonly kind: "server"; readonly message: string };

/** Итог шага входа. */
export type LoginOutcome =
  | { readonly kind: "authenticated" }
  | { readonly kind: "chooseBranch"; readonly branches: readonly BranchDetailResponse[] }
  | { readonly kind: "failed"; readonly failure: LoginFailure };

/**
 * Первый шаг входа: проверяет [credentials] и запрашивает доступные филиалы.
 * Если филиал один — сразу входит в него; если несколько — просит выбрать.
 */
export async function submitCredentials(
  api: AuthApi,
  credentials: Credentials,
): Promise<LoginOutcome> {
  const result = await api.branches(credentials);
  if (!result.ok) {
    return { kind: "failed", failure: credentialsFailure(result.error) };
  }
  const { branches } = result.value;
  const [single] = branches;
  if (branches.length === 1 && single !== undefined) {
    return submitBranch(api, credentials, single.id);
  }
  if (branches.length === 0) {
    return { kind: "failed", failure: { kind: "noBranches" } };
  }
  return { kind: "chooseBranch", branches };
}

/** Второй шаг входа: вход в выбранный филиал [branchId]. */
export async function submitBranch(
  api: AuthApi,
  credentials: Credentials,
  branchId: BranchId,
): Promise<LoginOutcome> {
  const result = await api.login({ ...credentials, branchId });
  if (!result.ok) {
    return { kind: "failed", failure: loginFailure(result.error) };
  }
  return { kind: "authenticated" };
}

/** Причина отказа при проверке логина и пароля: любой отказ сервера — неверные данные. */
function credentialsFailure(error: ApiError): LoginFailure {
  switch (error.kind) {
    case "business":
    case "unauthenticated":
      return { kind: "invalidCredentials" };
    case "unavailable":
    case "contract":
      return { kind: "unavailable" };
  }
}

/** Причина отказа при входе в филиал: сообщение сервера, если оно есть. */
function loginFailure(error: ApiError): LoginFailure {
  switch (error.kind) {
    case "business":
      return error.message === ""
        ? { kind: "invalidCredentials" }
        : { kind: "server", message: error.message };
    case "unauthenticated":
      return { kind: "invalidCredentials" };
    case "unavailable":
    case "contract":
      return { kind: "unavailable" };
  }
}
