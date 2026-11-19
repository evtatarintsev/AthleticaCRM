import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { Fragment } from "react";
import type { LocalDate, ScheduleSessionSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { WEEK_DAYS } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import { durationLabel, occupiedHours, sessionsAt } from "./scheduleGrid";
import { scheduleCardColors } from "./scheduleColors";
import { ScheduleSessionTags, ScheduleTag } from "./ScheduleSessionTags";
import { longDayLabel, sessionPhaseOf, utcDate, type OrgNow } from "./scheduleTime";
import type { ScheduleSearch } from "./scheduleWeek";

/**
 * Недельная сетка занятий: дни недели — колонки, часы начала — строки; колонка сегодняшнего
 * по [now] дня выделена. День в шапке — ссылка [dayLink] на режим «День», рядом — действие
 * добавления занятия [onAddSession]; без занятий под шапкой строка с [emptyText].
 */
export function ScheduleWeekGrid({
  weekDates,
  sessions,
  now,
  emptyText,
  dayLink,
  onAddSession,
}: {
  readonly weekDates: readonly LocalDate[];
  readonly sessions: readonly ScheduleSessionSchema[];
  readonly now: OrgNow;
  readonly emptyText: string;
  readonly dayLink: (date: LocalDate) => ScheduleSearch;
  readonly onAddSession: (date: LocalDate) => void;
}) {
  const { t, locale } = useI18n();
  const hours = occupiedHours(sessions);
  const dayFormat = new Intl.DateTimeFormat(locale, {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });

  return (
    <div className="overflow-x-auto rounded-xl border bg-card shadow-sm">
      <div
        className="grid"
        style={{
          gridTemplateColumns: "56px repeat(7, minmax(136px, 1fr))",
          minWidth: "calc(56px + 7 * 136px)",
        }}
      >
        <div className="border-b" />
        {weekDates.map((date, index) => {
          const day = WEEK_DAYS[index];
          const weekDay = day === undefined ? "" : t(`day.${day}`);
          const label = `${weekDay} ${dayFormat.format(utcDate(date))}`;
          const today = date === now.date;
          const count = sessions.filter((s) => s.date === date && s.status !== "CANCELLED").length;
          return (
            <div
              key={date}
              data-today={today || undefined}
              className={cn(
                "group flex min-w-0 items-center gap-2 border-b border-l py-2 pr-2 pl-3",
                today && "bg-primary/5",
              )}
            >
              <Link
                to="/schedule"
                search={dayLink(date)}
                aria-label={t("schedule.openDay", { day: longDayLabel(locale, date) })}
                className="-ml-1.5 inline-flex items-center gap-2 rounded-lg px-1.5 py-0.5 hover:bg-accent"
              >
                <span
                  className={cn(
                    "text-xs font-semibold tracking-wide text-muted-foreground uppercase",
                    today && "text-primary",
                  )}
                >
                  {weekDay}
                </span>
                <span
                  className={cn(
                    "min-w-[26px] text-center text-lg leading-[26px] font-bold tabular-nums",
                    today && "rounded-lg bg-primary px-1.5 text-primary-foreground",
                    date < now.date && "text-muted-foreground",
                  )}
                >
                  {utcDate(date).getUTCDate()}
                </span>
              </Link>
              {count > 0 && <span className="text-xs text-muted-foreground">{count}</span>}
              <Button
                variant="ghost"
                size="icon"
                className="ml-auto size-7 text-muted-foreground opacity-40 group-hover:opacity-100 focus-visible:opacity-100"
                aria-label={t("schedule.addSession", { day: label })}
                onClick={() => {
                  onAddSession(date);
                }}
              >
                <PlusIcon aria-hidden />
              </Button>
            </div>
          );
        })}

        {hours.length === 0 && (
          <p className="col-span-full py-14 text-center text-muted-foreground">{emptyText}</p>
        )}

        {hours.map((hour) => (
          <Fragment key={`hour-${hour.toString()}`}>
            <div className="border-t pt-2.5 pr-2.5 text-right text-xs text-muted-foreground tabular-nums">
              {hour.toString().padStart(2, "0")}:00
            </div>
            {weekDates.map((date) => (
              <div
                key={`${date}-${hour.toString()}`}
                className={cn(
                  "flex min-w-0 flex-col gap-1.5 border-t border-l p-1.5",
                  date === now.date && "bg-primary/5",
                )}
              >
                {sessionsAt(sessions, date, hour).map((session) => (
                  <SessionCard key={session.id} session={session} now={now} />
                ))}
              </div>
            ))}
          </Fragment>
        ))}
      </div>
    </div>
  );
}

/**
 * Карточка занятия: цветная полоса по ключу палитры, время и продолжительность, группа, тренеры,
 * зал, дисциплины и метки состояния в момент [now]. Прошедшие приглушены, отменённые зачёркнуты.
 */
function SessionCard({
  session,
  now,
}: {
  readonly session: ScheduleSessionSchema;
  readonly now: OrgNow;
}) {
  const { t } = useI18n();
  const colors = scheduleCardColors(session.colorKey);
  const phase = sessionPhaseOf(session, now);
  const cancelled = phase.kind === "cancelled";
  return (
    <Link
      to="/sessions/$sessionId"
      params={{ sessionId: session.id }}
      className={cn(
        "relative flex min-w-0 flex-col gap-px overflow-hidden rounded-[9px] py-[7px] pr-[9px] pl-3 text-[12.5px] leading-[1.35] text-foreground transition-[filter] hover:brightness-[.97] dark:hover:brightness-110",
        colors.tint,
        phase.kind === "past" && "opacity-55",
        phase.kind === "live" && "ring-2 ring-primary",
        cancelled &&
          "bg-[repeating-linear-gradient(135deg,transparent_0_7px,var(--color-card)_7px_14px)] opacity-70",
      )}
    >
      <span aria-hidden className={cn("absolute inset-y-0 left-0 w-[3px]", colors.bar)} />
      <span
        className={cn(
          "flex flex-wrap items-baseline gap-x-1.5 font-semibold whitespace-nowrap tabular-nums",
          colors.ink,
          cancelled && "line-through",
        )}
      >
        {session.startTime}–{session.endTime}
        <span className="text-[11.5px] font-medium opacity-75">
          {durationLabel(t, session.startTime, session.endTime)}
        </span>
      </span>
      <span className={cn("truncate text-[13px] font-semibold", cancelled && "line-through")}>
        {session.group.name}
      </span>
      {session.coaches.length > 0 && (
        <span className="truncate text-foreground/75">
          {session.coaches.map((c) => c.name).join(", ")}
        </span>
      )}
      <span className="truncate text-foreground/75">
        {[session.hall.name, ...session.disciplines.map((d) => d.name)].join(" · ")}
      </span>
      <ScheduleSessionTags session={session} phase={phase}>
        <ScheduleTag tone="mute">{t("schedule.freeSeatsStub")}</ScheduleTag>
      </ScheduleSessionTags>
    </Link>
  );
}
