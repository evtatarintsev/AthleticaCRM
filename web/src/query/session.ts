import { useSuspenseQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { AuthMeResponse, UserPermission } from "@/api/generated/contracts";
import { sessionQuery } from "./queries";

/**
 * Сессия текущего пользователя. Страницы за логином рендерятся после проверки сессии
 * в раскладке, поэтому данные уже в кэше и компонент не приостанавливается.
 */
export function useSession(api: ApiClient): AuthMeResponse {
  return useSuspenseQuery(sessionQuery(api)).data;
}

/** Есть ли у сессии [session] право [permission]. */
export function hasPermission(session: AuthMeResponse, permission: UserPermission): boolean {
  return session.permissions.includes(permission);
}
