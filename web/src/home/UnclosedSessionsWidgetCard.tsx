import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetUnclosedSessions,
  LocalDate,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { journalAgeDays } from "./homeTime";
import { WidgetCard } from "./WidgetCard";

/** Незакрытые журналы от недавних к давним, с календарным возрастом. */
export function UnclosedSessionsWidgetCard({
  api,
  branchId,
  settings,
  widget,
  title,
  today,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidgetUnclosedSessions;
  readonly title: string;
  readonly today: LocalDate;
}) {
  const { t, format } = useI18n();
  const query = useQuery(
    apiQuery(api, branchId, "home/unclosed-sessions", { limit: widget.limit }),
  );
  const sessions = query.data?.sessions ?? [];
  return (
    <WidgetCard
      api={api}
      settings={settings}
      widget={widget}
      title={title}
      count={query.data?.total ?? 0}
      state={
        query.isPending
          ? "pending"
          : query.isError
            ? "error"
            : sessions.length === 0
              ? "empty"
              : "ready"
      }
      emptyKey="home.unclosedSessionsEmpty"
      errorKey="home.unclosedSessionsError"
    >
      <ul>
        {sessions.map((session) => {
          const age = journalAgeDays(session.date, today);
          return (
            <li key={session.sessionId}>
              <Link
                to="/sessions/$sessionId"
                params={{ sessionId: session.sessionId }}
                className="flex items-center gap-3 rounded-xl px-2.5 py-2.5 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
              >
                <span className="min-w-0 flex-1 text-sm">
                  <span className="block truncate font-medium">{session.groupName}</span>
                  <span className="block text-xs text-muted-foreground">
                    {age === 0 ? t("home.today") : format.date(session.date)},{" "}
                    {format.time(session.startTime)}–{format.time(session.endTime)} ·{" "}
                    {t("home.hallLabel", { name: session.hallName })}
                  </span>
                </span>
                {age > 0 && (
                  <span className="shrink-0 whitespace-nowrap rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-950 dark:text-amber-300">
                    {age === 1 ? t("home.yesterday") : t("home.daysAgo", { count: age })}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </WidgetCard>
  );
}
