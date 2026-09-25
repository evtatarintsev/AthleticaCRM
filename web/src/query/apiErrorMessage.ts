import type { ApiError } from "@/api/client";
import type { I18n } from "@/i18n/context";

/**
 * Текст ошибки API [error] на языке интерфейса [t]. Бизнес-ошибку сервер уже локализовал
 * по `Accept-Language`, поэтому показывается его сообщение.
 */
export function apiErrorMessage(t: I18n["t"], error: ApiError): string {
  switch (error.kind) {
    case "business":
      return error.message === "" ? t("error.serviceUnavailable") : error.message;
    case "unauthenticated":
      return t("error.sessionExpired");
    case "unavailable":
      return t("error.serviceUnavailable");
    case "contract":
      return t("error.contract");
  }
}
