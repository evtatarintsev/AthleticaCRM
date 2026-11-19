import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { Fragment } from "react";
import type { LocalDate, ScheduleSessionSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { WEEK_DAYS } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import { scheduleCardColors } from "./scheduleColors";
import { ScheduleSessionTags } from "./ScheduleSessionTags";
import {
  hasStarted,
  longDayLabel,
  sessionPhaseOf,
  timeOfMinutes,
  utcDate,
  type OrgNow,
} from "./scheduleTime";
import type { ScheduleSearch } from "./scheduleWeek";

/** Не отменённые занятия из [sessions]. */
function activeCount(sessions: readonly ScheduleSessionSchema[]): number {
  return sessions.filter((session) => session.status !== "CANCELLED").length;
}

/**
 * Строка занятия [session] в момент [now] — для режима «День» и недельного списка. На широком
 * экране колонки «время / группа и дисциплины / зал / тренеры», на узком — время слева,
 * зал и тренеры строкой под названием. Ведёт на страницу занятия.
 */
export function ScheduleSessionRow({
  session,
  now,
}: {
  readonly session: ScheduleSessionSchema;
  readonly now: OrgNow;
}) {
  const colors = scheduleCardColors(session.colorKey);
  const phase = sessionPhaseOf(session, now);
  const cancelled = phase.kind === "cancelled";
  const disciplines = session.disciplines.map((d) => d.name).join(", ");
  const coaches = session.coaches.map((c) => c.name).join(", ");
  const narrowDetails = [session.hall.name, coaches].filter((v) => v !== "");
  return (
    <Link
      to="/sessions/$sessionId"
      params={{ sessionId: session.id }}
      className={cn(
        "grid grid-cols-[50px_minmax(0,1fr)] items-start gap-3 px-4 py-3 text-sm hover:bg-accent",
        "md:grid-cols-[64px_minmax(0,1.4fr)_minmax(0,0.5fr)_minmax(0,1fr)] md:items-center md:gap-4",
        phase.kind === "past" && "opacity-60",
      )}
    >
      <span className="leading-tight font-semibold tabular-nums">
        {session.startTime}
        <span className="block font-normal text-muted-foreground">{session.endTime}</span>
      </span>
      <span className="relative min-w-0 space-y-1 pl-3">
        <span aria-hidden className={cn("absolute inset-y-0 left-0 w-[3px] rounded", colors.bar)} />
        <span className={cn("block font-semibold", cancelled && "line-through")}>
          {session.group.name}
        </span>
        <span className="block text-muted-foreground">
          {disciplines}
          <span className="md:hidden">
            {narrowDetails.map((v, i) => (i === 0 && disciplines === "" ? v : ` · ${v}`)).join("")}
          </span>
        </span>
        <ScheduleSessionTags session={session} phase={phase} />
      </span>
      <span className="hidden truncate text-muted-foreground md:block">{session.hall.name}</span>
      <span className="hidden truncate text-muted-foreground md:block">{coaches}</span>
    </Link>
  );
}

/**
 * Строки занятий [sessions] дня [date] по времени начала; для сегодняшнего по [now] дня —
 * отметка текущего времени после последнего начавшегося занятия.
 */
function SessionRows({
  sessions,
  date,
  now,
}: {
  readonly sessions: readonly ScheduleSessionSchema[];
  readonly date: LocalDate;
  readonly now: OrgNow;
}) {
  const { t } = useI18n();
  const firstUpcoming = sessions.findIndex((session) => !hasStarted(session, now));
  const nowAt = date !== now.date ? null : firstUpcoming < 0 ? sessions.length : firstUpcoming;
  const nowLine = (
    <li
      role="separator"
      aria-label={t("schedule.now", { time: timeOfMinutes(now.minutes) })}
      className="flex h-6 items-center gap-2 px-4 text-xs font-semibold text-primary tabular-nums after:h-0.5 after:flex-1 after:rounded after:bg-primary"
    >
      {timeOfMinutes(now.minutes)}
    </li>
  );
  return (
    <ul className="divide-y">
      {sessions.map((session, index) => (
        <Fragment key={session.id}>
          {index === nowAt && nowLine}
          <li>
            <ScheduleSessionRow session={session} now={now} />
          </li>
        </Fragment>
      ))}
      {nowAt === sessions.length && nowLine}
    </ul>
  );
}

/**
 * Режим «День»: полоса дней недели [weekDates] со ссылками [dayLink] и отметками цветов
 * неотменённых занятий, под ней — занятия выбранного дня [date] из недели [sessions].
 * Пустой день — сообщение [emptyText] и действие добавления занятия [onAddSession].
 */
export function ScheduleDayView({
  weekDates,
  sessions,
  date,
  now,
  emptyText,
  dayLink,
  onAddSession,
}: {
  readonly weekDates: readonly LocalDate[];
  readonly sessions: readonly ScheduleSessionSchema[];
  readonly date: LocalDate;
  readonly now: OrgNow;
  readonly emptyText: string;
  readonly dayLink: (date: LocalDate) => ScheduleSearch;
  readonly onAddSession: (date: LocalDate) => void;
}) {
  const { t, locale } = useI18n();
  const day = sessions.filter((session) => session.date === date);
  return (
    <div className="space-y-3">
      <nav aria-label={t("schedule.days")} className="grid grid-cols-7 gap-1.5">
        {weekDates.map((weekDate, index) => {
          const weekDay = WEEK_DAYS[index];
          const dots = sessions
            .filter((s) => s.date === weekDate && s.status !== "CANCELLED")
            .slice(0, 4);
          const selected = weekDate === date;
          return (
            <Link
              key={weekDate}
              to="/schedule"
              search={dayLink(weekDate)}
              aria-current={selected ? "date" : undefined}
              aria-label={longDayLabel(locale, weekDate)}
              className={cn(
                "flex flex-col items-center gap-0.5 rounded-xl border bg-card py-1.5 hover:border-foreground/30",
                selected &&
                  "border-foreground bg-foreground text-background hover:border-foreground",
              )}
            >
              <span className="text-[11px] font-semibold uppercase opacity-70">
                {weekDay === undefined ? "" : t(`day.${weekDay}`)}
              </span>
              <span
                className={cn(
                  "text-base font-bold tabular-nums",
                  weekDate === now.date && !selected && "text-primary",
                )}
              >
                {utcDate(weekDate).getUTCDate()}
              </span>
              <span className="flex h-1.5 items-center gap-0.5">
                {dots.map((s) => (
                  <i
                    key={s.id}
                    data-testid="day-dot"
                    className={cn("size-1.5 rounded-full", scheduleCardColors(s.colorKey).bar)}
                  />
                ))}
              </span>
            </Link>
          );
        })}
      </nav>
      <section className="rounded-md border">
        {day.length === 0 ? (
          <div className="space-y-2 px-4 py-10 text-center text-sm text-muted-foreground">
            <p>{emptyText}</p>
            <Button
              variant="link"
              onClick={() => {
                onAddSession(date);
              }}
            >
              <PlusIcon aria-hidden />
              {t("schedule.addSessionAction")}
            </Button>
          </div>
        ) : (
          <SessionRows sessions={day} date={date} now={now} />
        )}
      </section>
    </div>
  );
}

/**
 * Неделя на узком экране: занятия [sessions] недели [weekDates] списком по дням с датой и
 * числом занятий; дни без занятий опущены, пустая неделя — сообщение [emptyText].
 */
export function ScheduleWeekList({
  weekDates,
  sessions,
  now,
  emptyText,
}: {
  readonly weekDates: readonly LocalDate[];
  readonly sessions: readonly ScheduleSessionSchema[];
  readonly now: OrgNow;
  readonly emptyText: string;
}) {
  const { t, locale } = useI18n();
  const days = weekDates
    .map((date) => ({ date, sessions: sessions.filter((s) => s.date === date) }))
    .filter((day) => day.sessions.length > 0);
  if (days.length === 0) {
    return (
      <p className="rounded-md border px-4 py-10 text-center text-sm text-muted-foreground">
        {emptyText}
      </p>
    );
  }
  return (
    <div className="space-y-4">
      {days.map((day) => {
        const label = longDayLabel(locale, day.date);
        return (
          <section key={day.date} aria-label={label} className="space-y-2">
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="text-xs font-semibold text-muted-foreground uppercase">
                {day.date === now.date ? t("schedule.dayLabelToday", { day: label }) : label}
              </h2>
              <span className="text-sm text-muted-foreground">
                {t("schedule.sessionCount", { count: activeCount(day.sessions) })}
              </span>
            </div>
            <div className="rounded-md border">
              <SessionRows sessions={day.sessions} date={day.date} now={now} />
            </div>
          </section>
        );
      })}
    </div>
  );
}
