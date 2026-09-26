import { Fragment } from "react";
import type { LocalDate, ScheduleSessionSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { WEEK_DAYS } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import { durationLabel, occupiedHours, sessionsAt } from "./scheduleGrid";
import { scheduleCardColors } from "./scheduleColors";

/** Дата [date] (`YYYY-MM-DD`) как `Date` в UTC-полночь, без сдвига по часовому поясу браузера. */
function utcDate(date: LocalDate): Date {
  const [year = 1970, month = 1, day = 1] = date.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

/** Недельная сетка занятий: дни недели — колонки, часы начала — строки. */
export function ScheduleWeekGrid({
  weekDates,
  sessions,
}: {
  readonly weekDates: readonly LocalDate[];
  readonly sessions: readonly ScheduleSessionSchema[];
}) {
  const { t, locale } = useI18n();
  const hours = occupiedHours(sessions);
  const dayFormat = new Intl.DateTimeFormat(locale, {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });

  return (
    <div className="overflow-x-auto rounded-md border">
      <div
        className="grid"
        style={{
          gridTemplateColumns: "64px repeat(7, minmax(150px, 1fr))",
          minWidth: "calc(64px + 7 * 150px)",
        }}
      >
        <div className="border-b border-r bg-muted/40" />
        {weekDates.map((date, index) => {
          const day = WEEK_DAYS[index];
          return (
            <div
              key={date}
              className="border-b border-r px-2 py-2 text-center text-sm font-medium last:border-r-0"
            >
              {day !== undefined && t(`day.${day}`)} {dayFormat.format(utcDate(date))}
            </div>
          );
        })}

        {hours.map((hour) => (
          <Fragment key={`hour-${hour.toString()}`}>
            <div className="border-r px-2 py-2 text-right text-xs text-muted-foreground">
              {hour.toString().padStart(2, "0")}:00
            </div>
            {weekDates.map((date) => (
              <div
                key={`${date}-${hour.toString()}`}
                className="space-y-1 border-r border-b p-1 last:border-r-0"
              >
                {sessionsAt(sessions, date, hour).map((session) => (
                  <SessionCard key={session.id} session={session} />
                ))}
              </div>
            ))}
          </Fragment>
        ))}
      </div>
    </div>
  );
}

/** Карточка занятия: время, продолжительность, группа, тренеры, зал, дисциплины. */
function SessionCard({ session }: { readonly session: ScheduleSessionSchema }) {
  const { t } = useI18n();
  const colors = scheduleCardColors(session.colorKey);
  const cancelled = session.status === "CANCELLED";
  return (
    <article
      className={cn(
        "space-y-1 rounded-md border p-2 text-xs",
        colors.container,
        colors.text,
        cancelled && "opacity-50",
      )}
    >
      <p className={cn("font-medium", cancelled && "line-through")}>
        {session.startTime}–{session.endTime} ·{" "}
        {durationLabel(t, session.startTime, session.endTime)}
      </p>
      {cancelled && (
        <span className="inline-block rounded-full bg-destructive/10 px-1.5 py-0.5 text-[10px] font-medium text-destructive">
          {t("schedule.cancelled")}
        </span>
      )}
      <p className="truncate font-medium">{session.group.name}</p>
      {session.coaches.length > 0 && (
        <p className="truncate">{session.coaches.map((c) => c.name).join(", ")}</p>
      )}
      <p className="truncate">{session.hall.name}</p>
      {session.disciplines.length > 0 && (
        <p className="truncate">{session.disciplines.map((d) => d.name).join(", ")}</p>
      )}
      <span className="inline-block rounded-full bg-background/60 px-1.5 py-0.5 text-[10px]">
        {t("schedule.freeSeatsStub")}
      </span>
    </article>
  );
}
