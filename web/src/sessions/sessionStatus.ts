import type { SessionStatus } from "@/api/generated/contracts";
import type { PlainMessageKey } from "@/i18n/context";

/** Ключ подписи статуса занятия [status] в словаре. */
export function sessionStatusLabelKey(status: SessionStatus): PlainMessageKey {
  switch (status) {
    case "SCHEDULED":
      return "sessionDetail.status.SCHEDULED";
    case "COMPLETED":
      return "sessionDetail.status.COMPLETED";
    case "CANCELLED":
      return "sessionDetail.status.CANCELLED";
  }
}

/** Действия над занятием, недоступные для статуса [status] (сценарий «отменённое занятие»). */
export function isEditableStatus(status: SessionStatus): boolean {
  return status === "SCHEDULED";
}
