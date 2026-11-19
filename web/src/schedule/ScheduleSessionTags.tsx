import type { ScheduleSessionSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import type { SessionPhase } from "./scheduleTime";

/**
 * Метки состояния занятия [session] в состоянии [phase]: «отменено», «идёт сейчас», «через N мин»,
 * «перенос», «разовое», «замена тренера». Общие для карточки сетки и строки списка;
 * меток посещаемости нет — её отмечают на странице занятия.
 */
export function ScheduleSessionTags({
  session,
  phase,
}: {
  readonly session: ScheduleSessionSchema;
  readonly phase: SessionPhase;
}) {
  const { t } = useI18n();
  const tags: { readonly label: string; readonly className: string }[] = [];
  switch (phase.kind) {
    case "cancelled":
      tags.push({
        label: t("schedule.cancelled"),
        className: "bg-destructive/10 text-destructive",
      });
      break;
    case "live":
      tags.push({ label: t("schedule.tag.live"), className: "bg-primary text-primary-foreground" });
      break;
    case "soon":
      tags.push({
        label: t("schedule.tag.soon", { count: phase.minutes }),
        className: "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
      });
      break;
    case "past":
    case "planned":
      break;
  }
  if (session.rescheduledFrom !== null) {
    tags.push({
      label: t("schedule.tag.rescheduled"),
      className: "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
    });
  }
  if (session.isManual) {
    tags.push({
      label: t("schedule.tag.manual"),
      className: "bg-background/70 text-muted-foreground",
    });
  }
  if (session.coachesOverridden) {
    tags.push({
      label: t("schedule.tag.coachesOverridden"),
      className: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
    });
  }
  if (tags.length === 0) {
    return null;
  }
  return (
    <span className="flex flex-wrap gap-1">
      {tags.map((tag) => (
        <span
          key={tag.label}
          className={cn(
            "inline-flex h-5 items-center rounded-full px-2 text-[11px] font-semibold whitespace-nowrap no-underline",
            tag.className,
          )}
        >
          {tag.label}
        </span>
      ))}
    </span>
  );
}
