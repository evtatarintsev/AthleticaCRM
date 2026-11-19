import type { ReactNode } from "react";
import type { ScheduleSessionSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import type { SessionPhase } from "./scheduleTime";

/** Оформление метки: нейтральная, информационная, предупреждение, ошибка, акцент. */
type TagTone = "mute" | "info" | "warn" | "bad" | "brand";

const TONES: Readonly<Record<TagTone, string>> = {
  mute: "bg-background text-muted-foreground",
  info: "bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-200",
  warn: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200",
  bad: "bg-destructive/10 text-destructive",
  brand: "bg-primary text-primary-foreground",
};

/** Метка [children] в оформлении [tone]. */
export function ScheduleTag({
  tone,
  children,
}: {
  readonly tone: TagTone;
  readonly children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex h-[19px] items-center rounded-full px-[7px] text-[11px] font-semibold whitespace-nowrap",
        TONES[tone],
      )}
    >
      {children}
    </span>
  );
}

/**
 * Метки состояния занятия [session] в состоянии [phase]: «отменено», «идёт сейчас», «через N мин»,
 * «перенос», «разовое», «замена тренера», и после них [children]. Общие для карточки сетки и
 * строки списка; меток посещаемости нет — её отмечают на странице занятия.
 */
export function ScheduleSessionTags({
  session,
  phase,
  children,
}: {
  readonly session: ScheduleSessionSchema;
  readonly phase: SessionPhase;
  readonly children?: ReactNode;
}) {
  const { t } = useI18n();
  const tags: { readonly label: string; readonly tone: TagTone }[] = [];
  switch (phase.kind) {
    case "cancelled":
      tags.push({ label: t("schedule.cancelled"), tone: "bad" });
      break;
    case "live":
      tags.push({ label: t("schedule.tag.live"), tone: "brand" });
      break;
    case "soon":
      tags.push({ label: t("schedule.tag.soon", { count: phase.minutes }), tone: "info" });
      break;
    case "past":
    case "planned":
      break;
  }
  if (session.rescheduledFrom !== null) {
    tags.push({ label: t("schedule.tag.rescheduled"), tone: "info" });
  }
  if (session.isManual) {
    tags.push({ label: t("schedule.tag.manual"), tone: "mute" });
  }
  if (session.coachesOverridden) {
    tags.push({ label: t("schedule.tag.coachesOverridden"), tone: "warn" });
  }
  if (tags.length === 0 && children === undefined) {
    return null;
  }
  return (
    <span className="mt-1 flex flex-wrap gap-1">
      {tags.map((tag) => (
        <ScheduleTag key={tag.label} tone={tag.tone}>
          {tag.label}
        </ScheduleTag>
      ))}
      {children}
    </span>
  );
}
