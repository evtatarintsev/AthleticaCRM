import type { Translate } from "../i18n/messages";
import type { ru } from "../i18n/ru";
import type { ApiError } from "./client";

/** Текст ошибки API [error] для показа человеку; бизнес-ошибку сервер уже перевёл. */
export function errorText(error: ApiError, t: Translate<typeof ru>): string {
  switch (error.kind) {
    case "business":
      return error.message;
    case "unauthenticated":
    case "unavailable":
      return t("error.unavailable");
    case "contract":
      return t("error.contract");
  }
}
