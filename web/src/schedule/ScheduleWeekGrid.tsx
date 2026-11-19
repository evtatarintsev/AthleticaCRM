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
import { ScheduleSessionTags } from "./ScheduleSessionTags";
import { sessionPhaseOf, utcDate, type OrgNow } from "./scheduleTime";
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
          const label = `${day === undefined ? "" : t(`day.${day}`)} ${dayFormat.format(utcDate(date))}`;
          const today = date === now.date;
          return (
            <div
              key={date}
              data-today={today || undefined}
              className={cn(
                "flex items-center justify-center gap-1 border-b border-r px-2 py-1 text-sm font-medium last:border-r-0",
                today && "bg-primary/10 text-primary",
                date < now.date && "text-muted-foreground",
              )}
            >
              <Link
                to="/schedule"
                search={dayLink(date)}
                aria-label={t("schedule.openDay", { day: label })}
                className={cn("rounded-md px-1.5 py-0.5 hover:bg-accent", today && "font-semibold")}
              >
                {label}
              </Link>
              <Button
                variant="ghost"
                size="icon"
                className="size-7 text-muted-foreground"
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
          <p className="col-span-full py-12 text-center text-muted-foreground">{emptyText}</p>
        )}

        {hours.map((hour) => (
          <Fragment key={`hour-${hour.toString()}`}>
            <div className="border-r px-2 py-2 text-right text-xs text-muted-foreground">
              {hour.toString().padStart(2, "0")}:00
            </div>
            {weekDates.map((date) => (
              <div
                key={`${date}-${hour.toString()}`}
                className={cn(
                  "space-y-1 border-r border-b p-1 last:border-r-0",
                  date === now.date && "bg-primary/10",
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
        "relative block space-y-0.5 overflow-hidden rounded-md border py-1.5 pr-2 pl-3 text-xs hover:brightness-95",
        colors.container,
        colors.text,
        phase.kind === "past" && "opacity-55",
        phase.kind === "live" && "ring-2 ring-primary",
        cancelled &&
          "bg-[repeating-linear-gradient(135deg,transparent_0_7px,var(--color-background)_7px_14px)] opacity-70",
      )}
    >
      <span aria-hidden className={cn("absolute inset-y-0 left-0 w-1", colors.bar)} />
      <p className={cn("font-semibold tabular-nums", cancelled && "line-through")}>
        {session.startTime}–{session.endTime}{" "}
        <span className="font-normal opacity-75">
          {durationLabel(t, session.startTime, session.endTime)}
        </span>
      </p>
      <p className={cn("truncate font-semibold", cancelled && "line-through")}>
        {session.group.name}
      </p>
      {session.coaches.length > 0 && (
        <p className="truncate">{session.coaches.map((c) => c.name).join(", ")}</p>
      )}
      <p className="truncate">
        {[session.hall.name, ...session.disciplines.map((d) => d.name)].join(" · ")}
      </p>
      <ScheduleSessionTags session={session} phase={phase} />
      <span className="inline-block rounded-full bg-background/60 px-1.5 py-0.5 text-[10px]">
        {t("schedule.freeSeatsStub")}
      </span>
    </Link>
  );
}
