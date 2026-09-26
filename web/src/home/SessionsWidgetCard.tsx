import { useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";

/** Виджет главной «Занятия сегодня»: расписание на сегодня в филиале. */
export function SessionsWidgetCard({
  api,
  branchId,
  title,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly title: string;
}) {
  const { t, format } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "home/today-sessions"));
  const sessions = query.data?.sessions;
  return (
    <div className="flex-1 rounded-lg border bg-card lg:min-w-0">
      <div className="border-b px-4 py-3">
        <h2 className="font-medium">{title}</h2>
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
          <p className="p-4 text-sm text-destructive">{t("home.todaySessionsError")}</p>
        )}
        {!query.isPending && !query.isError && sessions?.length === 0 && (
          <p className="p-4 text-sm text-muted-foreground">{t("home.todaySessionsEmpty")}</p>
        )}
        {!query.isPending && !query.isError && sessions !== undefined && sessions.length > 0 && (
          <ul className="divide-y">
            {sessions.map((session) => (
              <li key={session.sessionId} className="space-y-0.5 px-2 py-2">
                <p className="font-medium">{session.groupName}</p>
                <p className="text-sm text-muted-foreground">
                  {format.time(session.startTime)}–{format.time(session.endTime)} ·{" "}
                  {t("home.hallLabel", { name: session.hallName })}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
