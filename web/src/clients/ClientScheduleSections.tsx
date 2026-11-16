import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CalendarClockIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientGroup } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
  ShowAllButton,
} from "./card/ClientCardBlock";
import { clientUpcomingQuery, upcomingSessions } from "./card/clientCardQueries";

/**
 * Ближайшие занятия клиента: занятия его групп на две недели вперёд,
 * кроме отменённых. Без групп запрос не делается.
 */
export function ClientUpcomingSection({
  api,
  branchId,
  groups,
  limit,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groups: readonly ClientGroup[];
  readonly limit: number;
}) {
  const { t, format } = useI18n();
  const [showAll, setShowAll] = useState(false);
  const schedule = useQuery(clientUpcomingQuery(api, branchId, groups));
  const sessions = upcomingSessions(schedule.data?.sessions);
  const shown = showAll ? sessions : sessions.slice(0, limit);

  return (
    <ClientCardBlock
      icon={<CalendarClockIcon aria-hidden />}
      title={t("clientOverview.upcoming")}
      count={sessions.length}
      footer={
        <ShowAllButton
          total={sessions.length}
          limit={limit}
          showAll={showAll}
          onToggle={() => {
            setShowAll(!showAll);
          }}
        />
      }
    >
      <BlockStatus
        isPending={groups.length > 0 && schedule.isPending}
        isError={schedule.isError}
        isEmpty={sessions.length === 0}
        empty={t(groups.length === 0 ? "clientCard.upcomingNoGroups" : "clientCard.upcomingEmpty")}
      />
      {shown.length > 0 && (
        <ul className="divide-y">
          {shown.map((session) => (
            <BlockRow key={session.id} aside={<BlockSub>{session.hall.name}</BlockSub>}>
              <Link
                to="/sessions/$sessionId"
                params={{ sessionId: session.id }}
                className="font-medium hover:underline"
              >
                {format.date(session.date)}
                <span className="max-sm:block">{format.time(session.startTime)}</span>
              </Link>
              {session.id === sessions[0]?.id && (
                <Pill tone="gray">{t("clientCard.quick.nextSession")}</Pill>
              )}
              <BlockSub>
                {[session.group.name, ...session.coaches.map((coach) => coach.name)].join(" · ")}
              </BlockSub>
            </BlockRow>
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}
