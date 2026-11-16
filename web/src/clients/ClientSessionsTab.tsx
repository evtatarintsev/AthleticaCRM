import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientGroup, ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { ClientUpcomingSection } from "./ClientScheduleSections";
import { clientAttendanceQuery, clientUnpaidSessionsQuery } from "./card/clientCardQueries";
import { attendanceHistory, type AttendanceFilter } from "./card/clientOverview";
import { FormAlert } from "@/forms/FormAlert";

export function ClientSessionsTab({
  api,
  branchId,
  clientId,
  groups,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly groups: readonly ClientGroup[];
}) {
  const { t, format } = useI18n();
  const [filter, setFilter] = useState<AttendanceFilter>("all");
  const attendance = useQuery(clientAttendanceQuery(api, branchId, clientId));
  const unpaid = useQuery(clientUnpaidSessionsQuery(api, branchId, clientId));
  return (
    <div className="space-y-4">
      <ClientUpcomingSection api={api} branchId={branchId} groups={groups} limit={100} />
      <section className="rounded-lg border bg-card p-4">
        <h2 className="font-medium">{t("clientOverview.history")}</h2>
        <div className="my-3 flex flex-wrap gap-2">
          {(["all", "PRESENT", "ABSENT"] as const).map((value) => (
            <Button
              key={value}
              size="sm"
              variant={filter === value ? "secondary" : "outline"}
              aria-pressed={filter === value}
              onClick={() => {
                setFilter(value);
              }}
            >
              {t(`clientOverview.filter.${value}`)} ·{" "}
              {attendanceHistory(attendance.data?.items, value).length}
            </Button>
          ))}
        </div>
        {(attendance.isError || unpaid.isError) && (
          <FormAlert message={t("clients.loadClientError")} />
        )}
        <ul className="divide-y">
          {attendanceHistory(attendance.data?.items, filter).map((item) => (
            <li key={item.sessionId} className="py-3 text-sm">
              <Link
                className="rounded font-medium hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                to="/sessions/$sessionId"
                params={{ sessionId: item.sessionId }}
              >
                {format.date(item.date)}
                <span className="max-sm:block">
                  {format.time(item.startTime)} · {item.group.name} →
                </span>
              </Link>
              <span className="ml-2">{t(`clientOverview.filter.${item.presence}`)}</span>
              {item.labels.map((label) => (
                <span key={label.id} className="ml-2 rounded bg-secondary px-2">
                  {label.name}
                </span>
              ))}
              {unpaid.data?.sessions.some((session) => session.sessionId === item.sessionId) ===
                true && <span className="ml-2 text-destructive">{t("clientOverview.unpaid")}</span>}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
