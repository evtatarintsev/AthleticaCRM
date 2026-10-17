import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetUnclosedSessions,
} from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { WidgetSettingsButton } from "./WidgetSettingsButton";

/**
 * Виджет главной «Незакрытые журналы»: прошедшие по времени занятия текущего филиала,
 * журнал которых не закрыт, от недавних к давним. Если таких занятий больше лимита,
 * внизу показано их общее число.
 */
export function UnclosedSessionsWidgetCard({
  api,
  branchId,
  settings,
  widget,
  title,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidgetUnclosedSessions;
  readonly title: string;
}) {
  const { t, format } = useI18n();
  const query = useQuery(
    apiQuery(api, branchId, "home/unclosed-sessions", { limit: widget.limit }),
  );
  const sessions = query.data?.sessions;
  const total = query.data?.total ?? 0;
  return (
    <div className="flex-1 rounded-lg border bg-card lg:min-w-0">
      <div className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <h2 className="min-w-0 flex-1 truncate font-medium">{title}</h2>
        <WidgetSettingsButton api={api} settings={settings} widget={widget} />
      </div>
      <div className="p-2">
        {query.isPending && (
          <div className="space-y-2 p-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        )}
        {!query.isPending && query.isError && (
          <p className="p-4 text-sm text-destructive">{t("home.unclosedSessionsError")}</p>
        )}
        {!query.isPending && !query.isError && sessions?.length === 0 && (
          <p className="p-4 text-sm text-muted-foreground">{t("home.unclosedSessionsEmpty")}</p>
        )}
        {!query.isPending && !query.isError && sessions !== undefined && sessions.length > 0 && (
          <ul className="divide-y">
            {sessions.map((session) => (
              <li key={session.sessionId}>
                <Link
                  to="/sessions/$sessionId"
                  params={{ sessionId: session.sessionId }}
                  className="block space-y-0.5 px-2 py-2 hover:bg-accent"
                >
                  <p className="font-medium">{session.groupName}</p>
                  <p className="text-sm text-muted-foreground">
                    {format.date(session.date)}, {format.time(session.startTime)}–
                    {format.time(session.endTime)} ·{" "}
                    {t("home.hallLabel", { name: session.hallName })}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
      {!query.isPending && !query.isError && total > (sessions?.length ?? 0) && (
        <p className="border-t px-4 py-2 text-center text-sm text-muted-foreground">
          {t("home.unclosedSessionsTotal", { total })}
        </p>
      )}
    </div>
  );
}
