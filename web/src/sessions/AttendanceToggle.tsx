import type { PlainMessageKey } from "@/i18n/context";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import type { AttendanceStatus } from "./sessionDetailMock";

/** Статусы посещаемости в порядке отображения переключателя. */
const ATTENDANCE_STATUSES: readonly AttendanceStatus[] = ["PRESENT", "ABSENT", "SICK", "LATE"];

/** Ключ словаря подписи статуса посещаемости [status]. */
function attendanceLabelKey(status: AttendanceStatus): PlainMessageKey {
  switch (status) {
    case "PRESENT":
      return "sessionDetail.attendance.PRESENT";
    case "ABSENT":
      return "sessionDetail.attendance.ABSENT";
    case "SICK":
      return "sessionDetail.attendance.SICK";
    case "LATE":
      return "sessionDetail.attendance.LATE";
  }
}

/**
 * Четырёхпозиционный переключатель статуса посещения одного участника
 * (был / не был / болел / опоздал).
 */
export function AttendanceToggle({
  value,
  onChange,
}: {
  readonly value: AttendanceStatus;
  readonly onChange: (status: AttendanceStatus) => void;
}) {
  const { t } = useI18n();
  return (
    <div role="group" className="inline-flex gap-1">
      {ATTENDANCE_STATUSES.map((status) => {
        const active = status === value;
        return (
          <button
            key={status}
            type="button"
            aria-pressed={active}
            onClick={() => {
              onChange(status);
            }}
            className={cn(
              "rounded-md border px-2 py-1 text-xs font-medium",
              active
                ? "border-primary bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent",
            )}
          >
            {t(attendanceLabelKey(status))}
          </button>
        );
      })}
    </div>
  );
}
