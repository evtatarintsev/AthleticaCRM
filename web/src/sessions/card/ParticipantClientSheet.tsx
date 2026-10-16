import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ExternalLinkIcon, HistoryIcon } from "lucide-react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientAttendanceItemSchema,
  ClientDetailResponse,
  ClientId,
  MembershipSchema,
} from "@/api/generated/contracts";
import { ClientSubscriptionsSection } from "@/clients/ClientSubscriptionsSection";
import { ClientContactList } from "@/clients/card/ClientContactList";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
} from "@/clients/card/ClientCardBlock";
import { BalanceTile, LastVisitTile, MembershipTiles } from "@/clients/card/ClientCardQuickInfo";
import {
  attendanceWith,
  clientAttendanceQuery,
  membershipsQuery,
} from "@/clients/card/clientCardQueries";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";

/** Сколько последних отметок посещаемости показывается в панели. */
const RECENT_ATTENDANCE = 10;

/** Сколько абонементов показывается в панели до «Все». */
const MEMBERSHIPS_SHOWN = 3;

/** Состояние запроса отметок посещаемости клиента. */
interface AttendanceState {
  readonly data: { readonly items: readonly ClientAttendanceItemSchema[] } | undefined;
  readonly isPending: boolean;
  readonly isError: boolean;
}

/** Участник занятия, чья карточка открыта в панели. */
export interface ParticipantRef {
  readonly clientId: ClientId;
  /** Имя из журнала: показывается, пока карточка клиента загружается. */
  readonly name: string;
}

/**
 * Упрощённая карточка участника [participant] в боковой панели поверх карточки занятия,
 * с именем в заголовке: группы, контакты со ссылками для звонка и сообщения, баланс, действующий абонемент, последнее
 * посещение, абонементы и последние отметки посещаемости. Полная карточка клиента —
 * по ссылке «Открыть карточку». `null` — панель закрыта.
 */
export function ParticipantClientSheet({
  api,
  participant,
  onClose,
}: {
  readonly api: ApiClient;
  readonly participant: ParticipantRef | null;
  readonly onClose: () => void;
}) {
  return (
    <EditSheet
      open={participant !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={participant?.name ?? ""}
      size="lg"
    >
      {participant !== null && <ParticipantClientCard api={api} participant={participant} />}
    </EditSheet>
  );
}

/** Содержимое панели: загружает карточку клиента [participant] и его абонементы и посещения. */
function ParticipantClientCard({
  api,
  participant,
}: {
  readonly api: ApiClient;
  readonly participant: ParticipantRef;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const clientId = participant.clientId;
  const client = useQuery(apiQuery(api, branchId, "clients/detail", { id: clientId }));
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const attendance = useQuery(clientAttendanceQuery(api, branchId, clientId));

  return (
    <EditSheetBody>
      <div className="flex flex-wrap items-center gap-3">
        <Avatar
          api={api}
          uploadId={client.data?.avatarId ?? null}
          name={client.data?.name ?? participant.name}
          className="size-12 text-base"
        />
        <p className="min-w-0 flex-1 text-sm text-muted-foreground">
          {client.data?.groups.map((group) => group.name).join(" · ")}
        </p>
        <Button variant="outline" size="sm" asChild>
          <Link to="/clients/$clientId" params={{ clientId }}>
            <ExternalLinkIcon aria-hidden />
            {t("participantCard.openFull")}
          </Link>
        </Button>
      </div>

      {client.data === undefined ? (
        client.isError ? (
          <FormAlert message={t("clients.loadClientError")} />
        ) : (
          <div className="space-y-4">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        )
      ) : (
        <LoadedClient
          api={api}
          branchId={branchId}
          client={client.data}
          memberships={memberships.data?.memberships}
          attendance={attendance}
        />
      )}
    </EditSheetBody>
  );
}

/** Карточка загруженного клиента [client]: плитки, контакты, абонементы и посещения. */
function LoadedClient({
  api,
  branchId,
  client,
  memberships,
  attendance,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  /** Абонементы клиента; `undefined` — ещё загружаются. */
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly attendance: AttendanceState;
}) {
  const { t } = useI18n();
  return (
    <>
      <dl className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <BalanceTile balance={client.balance} onClick={undefined} />
        <MembershipTiles memberships={memberships} onOpen={undefined} />
        <LastVisitTile
          lastVisit={
            attendance.data === undefined
              ? undefined
              : (attendanceWith(attendance.data.items, "PRESENT")[0] ?? null)
          }
        />
      </dl>

      <section className="rounded-lg border bg-card">
        <h4 className="border-b px-4 py-2.5 font-medium">{t("clients.contacts")}</h4>
        <ClientContactList contacts={client.contacts} />
      </section>

      <ClientSubscriptionsSection
        api={api}
        branchId={branchId}
        clientId={client.id}
        limit={MEMBERSHIPS_SHOWN}
      />

      <RecentAttendance attendance={attendance} />
    </>
  );
}

/** Последние [RECENT_ATTENDANCE] отметок посещаемости — и посещения, и пропуски, от новых к старым. */
function RecentAttendance({ attendance }: { readonly attendance: AttendanceState }) {
  const { t, format } = useI18n();
  const items = (attendance.data?.items ?? [])
    .toSorted((a, b) => `${b.date}T${b.startTime}`.localeCompare(`${a.date}T${a.startTime}`))
    .slice(0, RECENT_ATTENDANCE);

  return (
    <ClientCardBlock
      icon={<HistoryIcon aria-hidden />}
      title={t("participantCard.recentAttendance")}
      count={null}
    >
      <BlockStatus
        isPending={attendance.isPending}
        isError={attendance.isError}
        isEmpty={items.length === 0}
        empty={t("participantCard.attendanceEmpty")}
      />
      {items.length > 0 && (
        <ul className="divide-y">
          {items.map((item) => (
            <BlockRow
              key={item.sessionId}
              aside={
                <span className="flex flex-wrap justify-end gap-1">
                  {item.presence === "PRESENT" ? (
                    <Pill tone="green">{t("sessionDetail.attendance.PRESENT")}</Pill>
                  ) : (
                    <Pill tone="red">{t("sessionDetail.attendance.ABSENT")}</Pill>
                  )}
                  {item.labels.map((label) => (
                    <Pill key={label.id} tone="gray">
                      {label.name}
                    </Pill>
                  ))}
                </span>
              }
            >
              <div className="font-medium">
                {format.date(item.date)}, {format.time(item.startTime)}
              </div>
              <BlockSub>{item.group.name}</BlockSub>
            </BlockRow>
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}
