import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CalendarDaysIcon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, GroupId, GroupSessionSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { dayOfWeekOf, todayLocalDate } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { sessionStatusLabelKey } from "@/sessions/sessionStatus";
import {
  dayPosition,
  defaultSessionsWindow,
  shiftSessionsWindow,
  type SessionsWindow,
} from "./groupSessionsWindow";

/**
 * Блок «Занятия» карточки группы: занятия группы за окно вокруг сегодняшнего дня
 * с переключением периода, закреплённые последнее и ближайшее занятия и переход
 * в календарь с фильтром по группе. Только чтение: строка ведёт в карточку занятия.
 */
export function GroupSessionsSection({
  api,
  branchId,
  groupId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groupId: GroupId;
}) {
  const { t, format } = useI18n();
  const today = todayLocalDate();
  const [period, setPeriod] = useState<SessionsWindow>(() => defaultSessionsWindow(today));
  const query = useQuery({
    ...apiQuery(api, branchId, "groups/sessions", { groupId, from: period.from, to: period.to }),
    placeholderData: keepPreviousData,
  });
  const data = query.data;

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="mr-auto text-sm font-medium text-muted-foreground">
          {t("groups.sessions.title")}
        </h2>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t("groups.sessions.prevPeriod")}
            onClick={() => {
              setPeriod(shiftSessionsWindow(period, -1));
            }}
          >
            <ChevronLeftIcon aria-hidden />
          </Button>
          <span className="text-sm whitespace-nowrap tabular-nums">
            {t("groups.sessions.range", {
              from: format.date(period.from),
              to: format.date(period.to),
            })}
          </span>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t("groups.sessions.nextPeriod")}
            onClick={() => {
              setPeriod(shiftSessionsWindow(period, 1));
            }}
          >
            <ChevronRightIcon aria-hidden />
          </Button>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/schedule" search={{ groupIds: [groupId] }}>
            <CalendarDaysIcon aria-hidden />
            {t("groups.sessions.calendar")}
          </Link>
        </Button>
      </div>

      {data !== undefined && (data.last !== null || data.next !== null) && (
        <ul className="divide-y rounded-md border bg-muted/40">
          {data.last !== null && <SessionRow session={data.last} pin={t("groups.sessions.last")} />}
          {data.next !== null && <SessionRow session={data.next} pin={t("groups.sessions.next")} />}
        </ul>
      )}

      {query.isPending && <Skeleton className="h-32 w-full" />}
      {query.isError && (
        <div className="space-y-2">
          <FormAlert message={t("groups.sessions.loadError")} />
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void query.refetch();
            }}
          >
            {t("action.retry")}
          </Button>
        </div>
      )}
      {data?.sessions.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("groups.sessions.empty")}</p>
      )}
      {data !== undefined && data.sessions.length > 0 && (
        <ul
          className={cn("divide-y rounded-md border", query.isPlaceholderData && "opacity-60")}
          aria-busy={query.isPlaceholderData}
        >
          {data.sessions.map((session, index) => {
            const position = dayPosition(session.date, today);
            const previous = data.sessions[index - 1];
            const startsToday =
              position === "today" &&
              (previous === undefined || dayPosition(previous.date, today) !== "today");
            return (
              <SessionRow
                key={session.id}
                session={session}
                pin={null}
                todayMark={startsToday ? t("groups.sessions.today") : null}
                highlighted={position === "today"}
              />
            );
          })}
        </ul>
      )}
    </section>
  );
}

/**
 * Строка занятия [session]: ссылка в карточку занятия с датой, временем, залом, тренерами,
 * статусом, бейджами и посещаемостью. [pin] — подпись закреплённой позиции
 * («Последнее»/«Следующее»), [todayMark] — подпись первой сегодняшней строки,
 * [highlighted] — выделение сегодняшнего занятия.
 */
function SessionRow({
  session,
  pin,
  todayMark = null,
  highlighted = false,
}: {
  readonly session: GroupSessionSchema;
  readonly pin: string | null;
  readonly todayMark?: string | null;
  readonly highlighted?: boolean;
}) {
  const { t, format } = useI18n();
  const cancelled = session.status === "CANCELLED";
  return (
    <li className={cn(highlighted && "bg-accent/50")}>
      {todayMark !== null && (
        <p className="px-4 pt-2 text-xs font-medium tracking-wide text-primary uppercase">
          {todayMark}
        </p>
      )}
      <Link
        to="/sessions/$sessionId"
        params={{ sessionId: session.id }}
        className="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-4 py-2.5 text-sm hover:bg-accent"
      >
        {pin !== null && (
          <span className="w-24 shrink-0 text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {pin}
          </span>
        )}
        <span className={cn("tabular-nums", cancelled && "text-muted-foreground line-through")}>
          {t(`day.${dayOfWeekOf(session.date)}`)} {format.date(session.date)}{" "}
          {format.time(session.startTime)}–{format.time(session.endTime)}
        </span>
        <span className="text-muted-foreground">{session.hall.name}</span>
        {session.coaches.length > 0 && (
          <span className="min-w-0 truncate text-muted-foreground">
            {session.coaches.map((coach) => coach.name).join(", ")}
            {session.coachesOverridden && ` (${t("groups.sessions.coachReplaced")})`}
          </span>
        )}
        {session.rescheduledFrom !== null && (
          <Badge>
            {t("groups.sessions.rescheduledFrom", { date: format.date(session.rescheduledFrom) })}
          </Badge>
        )}
        {session.isManual && <Badge>{t("groups.sessions.manual")}</Badge>}
        <span
          className={cn(
            "ml-auto text-xs",
            cancelled ? "text-destructive" : "text-muted-foreground",
          )}
        >
          {t(sessionStatusLabelKey(session.status))}
          {session.attendance !== null &&
            ` · ${t("groups.sessions.attendance", {
              present: session.attendance.present,
              total: session.attendance.total,
            })}`}
        </span>
      </Link>
    </li>
  );
}

/** Небольшой бейдж-пометка строки занятия с текстом [children]. */
function Badge({ children }: { readonly children: string }) {
  return (
    <span className="rounded-full border px-2 py-0.5 text-xs text-muted-foreground">
      {children}
    </span>
  );
}
