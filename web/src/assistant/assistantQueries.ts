import { queryOptions } from "@tanstack/react-query";
import type { ApiClient, ApiError } from "@/api/client";
import type { AssistantStatusResponse, EmployeeId } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { unwrap } from "@/query/apiFailure";

/** Ключ включает сотрудника: его ID уникален между организациями. */
export function assistantStatusQuery(api: ApiClient, employee: EmployeeId) {
  return queryOptions({
    queryKey: ["assistant", employee, "status"] as const,
    queryFn: async () => unwrap(await api.call("assistant/status")),
    refetchInterval: 30_000,
  });
}

/** Помощник доступен сотруднику: подключён, включён в организации и разрешён ему ([status]). */
export function assistantAllowed(status: AssistantStatusResponse | undefined): boolean {
  return status?.available === true && status.enabled && status.permitted;
}

/** Читается только личный текущий диалог; повтор чтения никогда не вызывает модель. */
export function assistantDialogQuery(api: ApiClient, employee: EmployeeId) {
  return queryOptions({
    queryKey: ["assistant", employee, "dialog"] as const,
    queryFn: async () => unwrap(await api.call("assistant/dialogs/current")),
  });
}

/** Все статические ошибки помощника локализуются по стабильному серверному коду. */
export function assistantCodeMessage(t: I18n["t"], code: string): string {
  switch (code) {
    case "ASSISTANT_UNAVAILABLE":
      return t("assistant.error.unavailable");
    case "ASSISTANT_DISABLED":
      return t("assistant.error.disabled");
    case "ASSISTANT_FORBIDDEN":
      return t("assistant.error.forbidden");
    case "ASSISTANT_LIMIT":
      return t("assistant.error.limit");
    case "ASSISTANT_BUSY":
      return t("assistant.error.busy");
    case "ASSISTANT_DRAFT_SUPERSEDED":
      return t("assistant.superseded");
    case "ASSISTANT_TIMEOUT":
    case "ASSISTANT_INTERRUPTED":
      return t("assistant.error.interrupted");
    case "ASSISTANT_PROVIDER_FAILURE":
      return t("assistant.error.provider");
    case "ASSISTANT_IMPORT_UNSUPPORTED_FORMAT":
      return t("assistant.attachError.unsupported");
    case "ASSISTANT_IMPORT_ATTACHMENT_SET":
      return t("assistant.attachError.mixed");
    case "ASSISTANT_IMPORT_TOO_MANY_PHOTOS":
      return t("assistant.attachError.tooManyPhotos");
    case "ASSISTANT_IMPORT_FILE_TOO_BIG":
      return t("assistant.attachError.tooBig");
    case "ASSISTANT_IMPORT_FILE_UNAVAILABLE":
      return t("assistant.error.importFileUnavailable");
    case "ASSISTANT_IMPORT_RECOGNITION_UNAVAILABLE":
      return t("assistant.error.importRecognitionUnavailable");
    case "ASSISTANT_IMPORT_TOO_MANY_ROWS":
      return t("assistant.error.importTooManyRows");
    case "ASSISTANT_IMPORT_FILE_INVALID":
      return t("assistant.error.importFileInvalid");
    case "ASSISTANT_IMPORT_DRAFT_STALE":
      return t("assistant.import.stale");
    default:
      return t("assistant.error.validation");
  }
}

/** Бизнес-ошибка помощника или штатная ошибка транспорта. */
export function assistantErrorMessage(t: I18n["t"], error: ApiError): string {
  return error.kind === "business"
    ? assistantCodeMessage(t, error.code)
    : apiErrorMessage(t, error);
}
