import type { ApiError } from "../api/client";
import type { BranchDetailResponse, BranchId } from "../api/generated/contracts";
import type { AuthApi } from "./authApi";
import { t } from "../i18n";

/** Учётные данные, введённые на экране входа. */
export interface Credentials {
  username: string;
  password: string;
}

/** Итог шага входа. */
export type LoginOutcome =
  | { kind: "authenticated" }
  | { kind: "chooseBranch"; branches: readonly BranchDetailResponse[] }
  | { kind: "error"; message: string };

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
    return { kind: "error", message: credentialsErrorMessage(result.error) };
  }
  const { branches } = result.value;
  const [single] = branches;
  if (branches.length === 1 && single !== undefined) {
    return submitBranch(api, credentials, single.id);
  }
  if (branches.length === 0) {
    return { kind: "error", message: t.errorNoBranches };
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
    return { kind: "error", message: loginErrorMessage(result.error) };
  }
  return { kind: "authenticated" };
}

/** Текст ошибки проверки логина и пароля: любой отказ сервера — неверные данные. */
function credentialsErrorMessage(error: ApiError): string {
  switch (error.kind) {
    case "business":
    case "unauthenticated":
      return t.errorInvalidCredentials;
    case "unavailable":
    case "contract":
      return t.errorServiceUnavailable;
  }
}

/** Текст ошибки входа в филиал: сообщение сервера, если оно есть. */
function loginErrorMessage(error: ApiError): string {
  switch (error.kind) {
    case "business":
      return error.message === "" ? t.errorInvalidCredentials : error.message;
    case "unauthenticated":
      return t.errorInvalidCredentials;
    case "unavailable":
    case "contract":
      return t.errorServiceUnavailable;
  }
}
