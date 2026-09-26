import type { TaskStatus } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { taskStatusLabelKey } from "./taskStatus";

/** Цвет фона бейджа статуса задачи [status], как в KMP-клиенте. */
function statusColor(status: TaskStatus): string {
  switch (status) {
    case "PENDING":
      return "#9E9E9E";
    case "IN_PROGRESS":
      return "#1976D2";
    case "PAUSED":
      return "#F57C00";
    case "COMPLETED":
      return "#388E3C";
  }
}

/** Цветной бейдж статуса задачи. */
export function TaskStatusBadge({ status }: { readonly status: TaskStatus }) {
  const { t } = useI18n();
  return (
    <span
      className="inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium text-white"
      style={{ backgroundColor: statusColor(status) }}
    >
      {t(taskStatusLabelKey(status))}
    </span>
  );
}
