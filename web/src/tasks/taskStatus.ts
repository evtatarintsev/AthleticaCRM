import type { TaskStatus } from "@/api/generated/contracts";
import type { PlainMessageKey } from "@/i18n/context";

/** Ключ подписи статуса задачи [status] в словаре. */
export function taskStatusLabelKey(status: TaskStatus): PlainMessageKey {
  switch (status) {
    case "PENDING":
      return "taskStatus.PENDING";
    case "IN_PROGRESS":
      return "taskStatus.IN_PROGRESS";
    case "PAUSED":
      return "taskStatus.PAUSED";
    case "COMPLETED":
      return "taskStatus.COMPLETED";
  }
}
