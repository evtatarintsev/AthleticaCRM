import { useQuery } from "@tanstack/react-query";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  LocalDate,
  PublicScheduleResponse,
  PublicScheduleSessionSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { addDays, dayOfWeekOf, mondayOf, weekDates } from "@/lib/localDate";
import { useInitialMediaQuery, WIDE_SCREEN } from "@/lib/useInitialMediaQuery";
import { cn } from "@/lib/utils";
import { unwrap } from "@/query/apiFailure";
import { longDayLabel, weekRangeLabel } from "@/schedule/scheduleTime";

/**
 * Публичная страница виджета расписания по ключу [widgetKey] для iframe на сайте клуба и
 * во ВКонтакте: без меню CRM и без входа. Весь период показа приходит одним запросом,
 * навигация по неделям и дням идёт на клиенте. Любая ошибка показывает «расписание недоступно» —
 * сервер не различает неизвестный, выключенный и удалённый виджет.
 */
export function PublicWidgetPage({ api, widgetKey }: { api: ApiClient; widgetKey: string }) {
  const { t } = useI18n();
  const schedule = useQuery({
    queryKey: ["public", "schedule-widget", widgetKey],
    queryFn: async () => unwrap(await api.call("public/schedule-widget", { key: widgetKey })),
  });
  return (
    <main className="mx-auto max-w-6xl p-4">
      {schedule.isPending && <Skeleton className="h-64 w-full" />}
      {schedule.isError && (
        <p role="status" className="py-16 text-center text-muted-foreground">
          {t("widget.unavailable")}
        </p>
      )}
      {schedule.data !== undefined && <WidgetSchedule schedule={schedule.data} />}
    </main>
  );
}

/** Расписание [schedule]: неделя на широком экране, день на узком; листать можно только в периоде показа. */
function WidgetSchedule({ schedule }: { schedule: PublicScheduleResponse }) {
  const { t, locale, format } = useI18n();
  const wide = useInitialMediaQuery(WIDE_SCREEN);
  const step = wide ? 7 : 1;
  const [date, setDate] = useState<LocalDate>(wide ? mondayOf(schedule.today) : schedule.today);
  const days = wide ? weekDates(date) : [date];
  const prev = addDays(date, -step);
  const next = addDays(date, step);
  const sessionsOf = (day: LocalDate) => schedule.sessions.filter((s) => s.date === day);
  const empty = days.every((day) => sessionsOf(day).length === 0);

  return (
    <section className="space-y-4">
      <header className="flex items-center gap-2">
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t(wide ? "schedule.prevWeek" : "schedule.prevDay")}
          disabled={prev < schedule.periodStart}
          onClick={() => {
            setDate(prev);
          }}
        >
          <ChevronLeftIcon aria-hidden />
        </Button>
        <h1 className="flex-1 text-center font-semibold first-letter:uppercase">
          {wide ? weekRangeLabel(locale, date) : longDayLabel(locale, date)}
        </h1>
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t(wide ? "schedule.nextWeek" : "schedule.nextDay")}
          disabled={next > schedule.periodEnd}
          onClick={() => {
            setDate(next);
          }}
        >
          <ChevronRightIcon aria-hidden />
        </Button>
      </header>
      {empty ? (
        <p className="py-12 text-center text-muted-foreground">
          {t(wide ? "schedule.empty" : "widget.emptyDay")}
        </p>
      ) : (
        <div className={cn("grid gap-3", wide && "grid-cols-7")}>
          {days.map((day) => (
            <section key={day} aria-label={longDayLabel(locale, day)} className="min-w-0 space-y-2">
              {wide && (
                <h2 className="text-sm font-medium text-muted-foreground">
                  {t(`day.${dayOfWeekOf(day)}`)} {format.dayMonth(day)}
                </h2>
              )}
              <ul className="space-y-2">
                {sessionsOf(day).map((session) => (
                  <WidgetSessionCard
                    key={`${session.startTime}-${session.groupName}`}
                    session={session}
                  />
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </section>
  );
}

/** Карточка занятия [session]; поля, выключенные в виджете (`null`) или пустые, не выводятся. */
function WidgetSessionCard({ session }: { session: PublicScheduleSessionSchema }) {
  const { t } = useI18n();
  const details = [
    session.disciplines?.join(", "),
    session.hall,
    session.coaches?.join(", "),
  ].filter((value): value is string => value !== undefined && value !== null && value !== "");
  return (
    <li
      className={cn(
        "space-y-1 rounded-md border bg-card p-2 text-sm",
        session.cancelled && "opacity-60",
      )}
    >
      <p className="font-medium tabular-nums">
        {session.startTime}–{session.endTime}
      </p>
      <p className={cn("font-semibold break-words", session.cancelled && "line-through")}>
        {session.groupName}
      </p>
      {session.cancelled && (
        <p className="text-xs font-medium text-destructive">{t("schedule.cancelled")}</p>
      )}
      {details.map((detail) => (
        <p key={detail} className="text-xs break-words text-muted-foreground">
          {detail}
        </p>
      ))}
    </li>
  );
}
