import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientDetailResponse,
  ClientId,
  MembershipId,
  TaskId,
} from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { TaskCreateSheet } from "@/tasks/TaskCreateSheet";
import { TaskSheet } from "@/tasks/TaskSheet";
import { PageHeader } from "@/ui/PageHeader";
import { ClientMessagesSection, ClientTasksSection } from "./ClientActivitySections";
import { ClientBalanceSheet } from "./ClientBalanceSheet";
import { ClientDocumentsSection } from "./ClientDocumentsSection";
import { ClientGroupsSection } from "./ClientGroupsSection";
import { ClientNotesSection } from "./ClientNotesSection";
import { ClientAttendanceSection, ClientUpcomingSection } from "./ClientScheduleSections";
import { ClientSubscriptionsSection } from "./ClientSubscriptionsSection";
import { IssueSubscriptionSheet } from "./IssueSubscriptionSheet";
import { MembershipSheet } from "./membership/MembershipSheet";
import {
  attendanceWith,
  clientAttendanceQuery,
  clientTasksQuery,
  clientUpcomingQuery,
  membershipsQuery,
  upcomingSessions,
} from "./card/clientCardQueries";
import { ClientCardQuickInfo } from "./card/ClientCardQuickInfo";
import { clientCustomFieldsQuery } from "./clientsQueries";
import { clientAlerts } from "./card/clientCardAlerts";
import { defaultTemplate, visibleBlocks, type ClientCardBlock } from "./card/clientCardBlocks";
import { ClientCardHeader } from "./card/ClientCardHeader";
import { ClientCardProfile } from "./card/ClientCardProfile";

/**
 * Карточка клиента: шапка с действиями и предупреждениями, слева — контакты и анкета,
 * справа — лента блоков (занятия, абонементы, группы, посещения, задачи, сообщения,
 * заметки, документы) в порядке шаблона карточки. «Выдать абонемент» открывает панель выдачи;
 * плитка баланса открывает панель «Баланс», если у сотрудника есть право `CAN_VIEW_CLIENT_BALANCE`.
 * Создание и карточка задачи клиента открываются панелями поверх карточки.
 */
export function ClientDetailPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const session = useSession(api);
  const branchId = session.currentBranch.id;
  const canViewBalance = hasPermission(session, "CAN_VIEW_CLIENT_BALANCE");
  const [balanceOpen, setBalanceOpen] = useState(false);
  const [membershipId, setMembershipId] = useState<MembershipId | null>(null);
  const [membershipOpen, setMembershipOpen] = useState(false);
  const [issueOpen, setIssueOpen] = useState(false);
  const [taskCreateOpen, setTaskCreateOpen] = useState(false);
  const [openTaskId, setOpenTaskId] = useState<TaskId | null>(null);
  const detailQuery = apiQuery(api, branchId, "clients/detail", { id: clientId });
  const client = useQuery(detailQuery);
  const customFieldDefs = useQuery(clientCustomFieldsQuery(api, branchId));
  const leadSources = useQuery(apiQuery(api, branchId, "lead-sources/list"));
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const tasks = useQuery(clientTasksQuery(api, branchId, clientId));
  const attendance = useQuery(clientAttendanceQuery(api, branchId, clientId));
  const upcoming = useQuery(clientUpcomingQuery(api, branchId, client.data?.groups ?? []));

  const refreshDetail = async () => {
    await queryClient.invalidateQueries({ queryKey: detailQuery.queryKey });
  };

  const toggleArchived = async (archived: boolean) => {
    const result = await api.call(archived ? "clients/archive" : "clients/restore", {
      clientIds: [clientId],
    });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await Promise.all([
      refreshDetail(),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] }),
    ]);
    toast.success(t(archived ? "clients.archivedToast" : "clients.restoredToast"));
  };

  if (client.data === undefined) {
    return (
      <section className="max-w-6xl">
        <PageHeader title={t("clients.title")} />
        {client.isPending && (
          <div className="space-y-4">
            <Skeleton className="h-36 w-full" />
            <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
              <Skeleton className="h-64 w-full" />
              <Skeleton className="h-64 w-full" />
            </div>
          </div>
        )}
        {client.isError && <FormAlert message={t("clients.loadClientError")} />}
      </section>
    );
  }

  const data = client.data;
  const leadSourceName =
    leadSources.data?.leadSources.find((source) => source.id === data.leadSourceId)?.name ?? null;
  const today = todayLocalDate();
  const alerts = clientAlerts({
    client: data,
    memberships: memberships.data?.memberships,
    tasks: tasks.data?.tasks,
    today,
    now: new Date(),
  });
  const openMembership = (id: MembershipId) => {
    setMembershipId(id);
    setMembershipOpen(true);
  };

  return (
    <section className="max-w-6xl space-y-4 pb-16">
      <ClientCardHeader
        api={api}
        client={data}
        leadSourceName={leadSourceName}
        quickInfo={
          <ClientCardQuickInfo
            balance={data.balance}
            onBalanceClick={
              canViewBalance
                ? () => {
                    setBalanceOpen(true);
                  }
                : undefined
            }
            memberships={memberships.data?.memberships}
            onMembershipOpen={openMembership}
            lastVisit={
              attendance.data === undefined
                ? undefined
                : (attendanceWith(attendance.data.items, "PRESENT")[0] ?? null)
            }
            nextSession={
              data.groups.length === 0
                ? null
                : upcoming.data === undefined
                  ? undefined
                  : (upcomingSessions(upcoming.data.sessions)[0] ?? null)
            }
          />
        }
        alerts={alerts}
        onToggleArchived={(archived) => {
          void toggleArchived(archived);
        }}
        onIssueSubscription={() => {
          setIssueOpen(true);
        }}
      />

      <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)] lg:items-start">
        <aside className="lg:sticky lg:top-4">
          <ClientCardProfile
            client={data}
            leadSourceName={leadSourceName}
            customFieldDefs={customFieldDefs.data ?? []}
            editable={data.state === "ACTIVE"}
          />
        </aside>
        <div className="space-y-4">
          {visibleBlocks(defaultTemplate()).map((block) => (
            <FeedBlock
              key={block.id}
              block={block}
              api={api}
              branchId={branchId}
              client={data}
              currentEmployeeId={session.employeeId}
              onChanged={refreshDetail}
              onMembershipOpen={openMembership}
              onTaskCreate={() => {
                setTaskCreateOpen(true);
              }}
              onTaskOpen={setOpenTaskId}
            />
          ))}
        </div>
      </div>

      <MembershipSheet
        membership={
          memberships.data?.memberships.find((membership) => membership.id === membershipId) ?? null
        }
        open={membershipOpen}
        api={api}
        branchId={branchId}
        clientId={clientId}
        onOpenChange={setMembershipOpen}
      />

      <TaskCreateSheet
        api={api}
        open={taskCreateOpen}
        onOpenChange={setTaskCreateOpen}
        client={{ id: data.id, name: data.name }}
      />
      <TaskSheet
        api={api}
        taskId={openTaskId}
        onClose={() => {
          setOpenTaskId(null);
        }}
      />

      <IssueSubscriptionSheet
        api={api}
        branchId={branchId}
        clientId={clientId}
        open={issueOpen}
        onOpenChange={setIssueOpen}
      />
      {canViewBalance && (
        <ClientBalanceSheet
          api={api}
          branchId={branchId}
          client={data}
          open={balanceOpen}
          onOpenChange={setBalanceOpen}
          onChanged={(updated) => {
            queryClient.setQueryData(detailQuery.queryKey, updated);
          }}
        />
      )}
    </section>
  );
}

/**
 * Блок ленты по его типу. [onTaskCreate] и [onTaskOpen] открывают панели задач
 * поверх карточки.
 */
function FeedBlock({
  block,
  api,
  branchId,
  client,
  currentEmployeeId,
  onChanged,
  onMembershipOpen,
  onTaskCreate,
  onTaskOpen,
}: {
  readonly block: ClientCardBlock;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly currentEmployeeId: string;
  readonly onChanged: () => Promise<void>;
  readonly onMembershipOpen: (id: MembershipId) => void;
  readonly onTaskCreate: () => void;
  readonly onTaskOpen: (taskId: TaskId) => void;
}) {
  const clientId = client.id;
  switch (block.type) {
    case "upcoming":
      return (
        <ClientUpcomingSection
          api={api}
          branchId={branchId}
          groups={client.groups}
          limit={block.limit}
        />
      );
    case "subscriptions":
      return (
        <ClientSubscriptionsSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          limit={block.limit}
          onOpen={onMembershipOpen}
        />
      );
    case "groups":
      return (
        <ClientGroupsSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          groups={client.groups}
          onChanged={onChanged}
        />
      );
    case "visits":
      return (
        <ClientAttendanceSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          presence="PRESENT"
          limit={block.limit}
        />
      );
    case "missed":
      return (
        <ClientAttendanceSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          presence="ABSENT"
          limit={block.limit}
        />
      );
    case "tasks":
      return (
        <ClientTasksSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          limit={block.limit}
          onCreate={onTaskCreate}
          onOpen={onTaskOpen}
        />
      );
    case "messages":
      return <ClientMessagesSection api={api} branchId={branchId} clientId={clientId} />;
    case "notes":
      return (
        <ClientNotesSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          currentEmployeeId={currentEmployeeId}
          limit={block.limit}
        />
      );
    case "documents":
      return (
        <ClientDocumentsSection
          api={api}
          clientId={clientId}
          docs={client.docs}
          onChanged={onChanged}
        />
      );
  }
}
