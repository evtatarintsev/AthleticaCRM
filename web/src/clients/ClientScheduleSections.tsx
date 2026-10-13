import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CalendarClockIcon, CalendarXIcon, CheckCircle2Icon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientAttendanceItemSchema,
  ClientGroup,
  ClientId,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
  ShowAllButton,
} from "./card/ClientCardBlock";
import {
  attendanceWith,
  clientAttendanceQuery,
  clientUpcomingQuery,
  upcomingSessions,
} from "./card/clientCardQueries";

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
      title={t("clientCard.block.upcoming")}
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
                {format.date(session.date)}, {format.time(session.startTime)}
              </Link>
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

/**
 * Блок отметок посещаемости: посещения ([presence] `PRESENT`) или пропуски (`ABSENT`)
 * за последние полгода. Оба блока и шапка делят один запрос.
 */
export function ClientAttendanceSection({
  api,
  branchId,
  clientId,
  presence,
  limit,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly presence: ClientAttendanceItemSchema["presence"];
  readonly limit: number;
}) {
  const { t, format } = useI18n();
  const [showAll, setShowAll] = useState(false);
  const attendance = useQuery(clientAttendanceQuery(api, branchId, clientId));
  const items = attendanceWith(attendance.data?.items, presence);
  const shown = showAll ? items : items.slice(0, limit);
  const present = presence === "PRESENT";

  return (
    <ClientCardBlock
      icon={present ? <CheckCircle2Icon aria-hidden /> : <CalendarXIcon aria-hidden />}
      title={t(present ? "clientCard.block.visits" : "clientCard.block.missed")}
      count={items.length}
      footer={
        <ShowAllButton
          total={items.length}
          limit={limit}
          showAll={showAll}
          onToggle={() => {
            setShowAll(!showAll);
          }}
        />
      }
    >
      <BlockStatus
        isPending={attendance.isPending}
        isError={attendance.isError}
        isEmpty={items.length === 0}
        empty={t(present ? "clientCard.visitsEmpty" : "clientCard.missedEmpty")}
      />
      {shown.length > 0 && (
        <ul className="divide-y">
          {shown.map((item) => (
            <BlockRow
              key={item.sessionId}
              aside={
                item.labels.length > 0 ? (
                  <span className="flex flex-wrap justify-end gap-1">
                    {item.labels.map((label) => (
                      <Pill key={label.id} tone="gray">
                        {label.name}
                      </Pill>
                    ))}
                  </span>
                ) : null
              }
            >
              <Link
                to="/sessions/$sessionId"
                params={{ sessionId: item.sessionId }}
                className="font-medium hover:underline"
              >
                {format.date(item.date)}, {format.time(item.startTime)}
              </Link>
              <BlockSub>{item.group.name}</BlockSub>
            </BlockRow>
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}
