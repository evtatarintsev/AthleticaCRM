import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetSessions,
  LocalDate,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { sessionStates } from "./homeTime";
import { WidgetCard } from "./WidgetCard";

/** Расписание на сегодня с состояниями по общим часам главной. */
export function SessionsWidgetCard({
  api,
  branchId,
  settings,
  widget,
  title,
  now,
  today,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidgetSessions;
  readonly title: string;
  readonly now: Date;
  readonly today: LocalDate;
}) {
  const { t, format } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "home/today-sessions"));
  const sessions = query.data?.sessions ?? [];
  const states = sessionStates(sessions, query.data?.date, today, now);
  return (
    <WidgetCard
      api={api}
      settings={settings}
      widget={widget}
      title={title}
      count={sessions.length}
      state={
        query.isPending
          ? "pending"
          : query.isError
            ? "error"
            : sessions.length === 0
              ? "empty"
              : "ready"
      }
      emptyKey="home.todaySessionsEmpty"
      errorKey="home.todaySessionsError"
    >
      <ul>
        {sessions.map((session, index) => (
          <li key={session.sessionId}>
            <Link
              to="/sessions/$sessionId"
              params={{ sessionId: session.sessionId }}
              data-state={states[index]}
              className={cn(
                "flex items-center gap-3 rounded-xl px-2.5 py-2.5 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring",
                states[index] === "past" && "opacity-55",
                states[index] === "live" && "bg-sky-50 dark:bg-sky-950/60",
              )}
            >
              <span className="flex shrink-0 flex-col text-sm leading-tight tabular-nums">
                <span
                  className={cn(
                    "font-semibold",
                    states[index] === "next" && "text-sky-700 dark:text-sky-300",
                  )}
                >
                  {format.time(session.startTime)}
                </span>
                <span className="text-xs text-muted-foreground">
                  {format.time(session.endTime)}
                </span>
              </span>
              <span
                className={cn(
                  "flex min-h-9 min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1 border-l-[3px] pl-3 text-sm font-medium",
                  states[index] === "live"
                    ? "border-sky-600 dark:border-sky-400"
                    : states[index] === "next"
                      ? "border-sky-300 dark:border-sky-700"
                      : "border-border",
                )}
              >
                <span className="min-w-0 break-words">{session.groupName}</span>
                {states[index] === "live" && (
                  <span className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-sky-700 px-2 py-0.5 text-xs font-semibold text-white dark:bg-sky-600">
                    <span aria-hidden className="size-1.5 rounded-full bg-current" />
                    {t("home.sessionLive")}
                  </span>
                )}
              </span>
              <span className="max-w-20 shrink-0 text-right text-xs text-muted-foreground">
                {t("home.hallLabel", { name: session.hallName })}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </WidgetCard>
  );
}
