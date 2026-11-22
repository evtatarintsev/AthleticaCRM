import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { ClientId, MembershipId, TaskId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { SaleCreateSheet } from "@/finance/SaleSheet";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { ConversationSheet } from "@/messaging/ConversationSheet";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { TaskCreateSheet } from "@/tasks/TaskCreateSheet";
import { TaskSheet } from "@/tasks/TaskSheet";
import { PageHeader } from "@/ui/PageHeader";
import { BackLink } from "@/ui/BackLink";
import { ClientTasksSection } from "./ClientActivitySections";
import { ClientBalanceSheet } from "./ClientBalanceSheet";
import { ClientDocumentsSection } from "./ClientDocumentsSection";
import { ClientOverviewTab } from "./ClientOverviewTab";
import { ClientSessionsTab } from "./ClientSessionsTab";
import { ClientCardSectionsSheet } from "./ClientCardSectionsSheet";
import { RepayDebtSheet } from "./membership/RepayDebt";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { ClientNotesSection } from "./ClientNotesSection";
import { ClientSubscriptionsSection } from "./ClientSubscriptionsSection";
import { IssueSubscriptionSheet } from "./IssueSubscriptionSheet";
import { MembershipSheet } from "./membership/MembershipSheet";
import {
  clientCardSectionsQuery,
  clientUnpaidSessionsQuery,
  clientTaskSummaryQuery,
  clientAttendanceQuery,
  membershipsQuery,
} from "./card/clientCardQueries";
import { ClientCardQuickInfo } from "./card/ClientCardQuickInfo";
import { clientCustomFieldsQuery } from "./clientsQueries";
import { clientAlerts } from "./card/clientCardAlerts";
import { ClientCardHeader } from "./card/ClientCardHeader";
import { ClientCardProfile } from "./card/ClientCardProfile";

/** Карточка клиента: одна страница и панели списков; изменения открываются в существующих панелях. */
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
  const [saleCreateOpen, setSaleCreateOpen] = useState(false);
  const canViewBalance = hasPermission(session, "CAN_VIEW_CLIENT_BALANCE");
  const [listPanel, setListPanel] = useState<"memberships" | "sessions" | "tasks" | "notes" | null>(
    null,
  );
  const [sectionsOpen, setSectionsOpen] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);
  const [repayId, setRepayId] = useState<MembershipId | null>(null);
  const [balanceOpen, setBalanceOpen] = useState(false);
  const [membershipId, setMembershipId] = useState<MembershipId | null>(null);
  const [membershipOpen, setMembershipOpen] = useState(false);
  const [issueOpen, setIssueOpen] = useState(false);
  const [taskCreateOpen, setTaskCreateOpen] = useState(false);
  const [openTaskId, setOpenTaskId] = useState<TaskId | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const detailQuery = apiQuery(api, branchId, "clients/detail", { id: clientId });
  const client = useQuery(detailQuery);
  const customFieldDefs = useQuery(clientCustomFieldsQuery(api, branchId));
  const leadSources = useQuery(apiQuery(api, branchId, "lead-sources/list"));
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const sections = useQuery(clientCardSectionsQuery(api, branchId));
  const flags = sections.data;
  const unpaid = useQuery(clientUnpaidSessionsQuery(api, branchId, clientId));
  const overdue = useQuery({
    ...clientTaskSummaryQuery(api, branchId, clientId, true),
    enabled: flags?.tasks === true,
  });
  const attendance = useQuery(clientAttendanceQuery(api, branchId, clientId));

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

  if (client.data === undefined)
    return (
      <section className="max-w-6xl space-y-4">
        <BackLink link={{ to: "/clients" }} />
        <PageHeader title={t("clients.title")} />
        {client.isPending && <Skeleton className="h-64 w-full" />}
        {client.isError && <FormAlert message={t("clients.loadClientError")} />}
      </section>
    );
  const data = client.data;
  const leadSourceName =
    leadSources.data?.leadSources.find((source) => source.id === data.leadSourceId)?.name ?? null;
  const alerts = clientAlerts({
    client: data,
    memberships: memberships.data?.memberships,
    unpaid: unpaid.data?.sessions,
    overdue: flags?.tasks === true ? overdue.data : undefined,
    today: todayLocalDate(),
  });
  const openMembership = (id: MembershipId) => {
    setMembershipId(id);
    setMembershipOpen(true);
  };
  const detailPanels = (
    <>
      {membershipOpen && (
        <MembershipSheet
          membership={
            memberships.data?.memberships.find((membership) => membership.id === membershipId) ??
            null
          }
          open={membershipOpen}
          api={api}
          branchId={branchId}
          clientId={clientId}
          onOpenChange={setMembershipOpen}
        />
      )}

      {taskCreateOpen && (
        <TaskCreateSheet
          api={api}
          open={taskCreateOpen}
          onOpenChange={setTaskCreateOpen}
          client={{ id: data.id, name: data.name }}
        />
      )}
      {openTaskId !== null && (
        <TaskSheet
          api={api}
          taskId={openTaskId}
          onClose={() => {
            setOpenTaskId(null);
          }}
        />
      )}

      {issueOpen && (
        <IssueSubscriptionSheet
          api={api}
          branchId={branchId}
          clientId={clientId}
          open={issueOpen}
          onOpenChange={setIssueOpen}
        />
      )}
    </>
  );
  return (
    <section className="max-w-6xl space-y-4 pb-24 sm:pb-16">
      <BackLink link={{ to: "/clients" }} />
      <ClientCardHeader
        api={api}
        client={data}
        leadSourceName={leadSourceName}
        chatEnabled={flags?.chat === true}
        onTaskCreate={
          flags?.tasks === true
            ? () => {
                setTaskCreateOpen(true);
              }
            : undefined
        }
        onAddGroup={() => {
          setGroupOpen(true);
        }}
        onSections={() => {
          setSectionsOpen(true);
        }}
        onToggleArchived={(archived) => {
          void toggleArchived(archived);
        }}
        onIssueSubscription={() => {
          setIssueOpen(true);
        }}
        onOpenChat={() => {
          setChatOpen(true);
        }}
      />
      {(sections.isError || unpaid.isError) && <FormAlert message={t("clients.loadClientError")} />}
      {data.state === "ACTIVE" && (
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setSaleCreateOpen(true);
          }}
        >
          {t("finance.sellService")}
        </Button>
      )}
      <SaleCreateSheet
        api={api}
        branchId={branchId}
        client={data}
        open={saleCreateOpen}
        onOpenChange={setSaleCreateOpen}
        onSaved={refreshDetail}
      />
      <ClientOverviewTab
        api={api}
        branchId={branchId}
        client={data}
        alerts={alerts}
        chat={flags?.chat === true}
        notes={flags?.notes === true}
        onChanged={refreshDetail}
        onChat={() => {
          setChatOpen(true);
        }}
        onNotes={() => {
          setListPanel("notes");
        }}
        onIssue={() => {
          setIssueOpen(true);
        }}
        onBalance={
          canViewBalance
            ? () => {
                setBalanceOpen(true);
              }
            : undefined
        }
        onRepay={setRepayId}
        onTask={setOpenTaskId}
        onTasks={() => {
          setListPanel("tasks");
        }}
        groupOpen={groupOpen}
        onGroupOpen={setGroupOpen}
        profile={
          <ClientCardProfile
            client={data}
            leadSourceName={leadSourceName}
            customFieldDefs={customFieldDefs.data ?? []}
            editable={data.state === "ACTIVE"}
          >
            {flags?.documents === true && (
              <ClientDocumentsSection
                api={api}
                clientId={clientId}
                docs={data.docs}
                onChanged={refreshDetail}
              />
            )}
          </ClientCardProfile>
        }
        tasks={
          flags?.tasks === true ? (
            <ClientTasksSection
              api={api}
              branchId={branchId}
              clientId={clientId}
              onCreate={() => {
                setTaskCreateOpen(true);
              }}
              onOpen={setOpenTaskId}
              onAll={() => {
                setListPanel("tasks");
              }}
            />
          ) : null
        }
        quickInfo={
          <ClientCardQuickInfo
            balance={data.balance}
            memberships={memberships.data?.memberships}
            attendance={attendance.data?.items}
            onBalanceClick={
              canViewBalance
                ? () => {
                    setBalanceOpen(true);
                  }
                : undefined
            }
            onMembershipOpen={openMembership}
            onMemberships={() => {
              setListPanel("memberships");
            }}
            onSessions={() => {
              setListPanel("sessions");
            }}
          />
        }
      />
      <EditSheet
        open={listPanel !== null}
        onOpenChange={(open) => {
          if (!open) setListPanel(null);
        }}
        title={
          listPanel === "memberships"
            ? t("clients.detail.subscriptions")
            : listPanel === "sessions"
              ? t("clientOverview.sessions")
              : listPanel === "tasks"
                ? t("tasks.title")
                : t("clients.detail.notes")
        }
        size="lg"
        closeOnOutside
      >
        <EditSheetBody>
          {listPanel === "memberships" && (
            <ClientSubscriptionsSection
              api={api}
              branchId={branchId}
              clientId={clientId}
              limit={100}
              onOpen={openMembership}
              onIssue={() => {
                setIssueOpen(true);
              }}
            />
          )}
          {listPanel === "sessions" && (
            <ClientSessionsTab
              api={api}
              branchId={branchId}
              clientId={clientId}
              groups={data.groups}
            />
          )}
          {listPanel === "tasks" && (
            <ClientTasksSection
              api={api}
              branchId={branchId}
              clientId={clientId}
              onCreate={() => {
                setTaskCreateOpen(true);
              }}
              onOpen={setOpenTaskId}
            />
          )}
          {listPanel === "notes" && (
            <ClientNotesSection
              api={api}
              branchId={branchId}
              clientId={clientId}
              currentEmployeeId={session.employeeId}
              limit={100}
            />
          )}
        </EditSheetBody>
        {listPanel !== null && detailPanels}
      </EditSheet>
      {listPanel === null && detailPanels}
      <ClientCardSectionsSheet api={api} open={sectionsOpen} onOpenChange={setSectionsOpen} />
      {repayId !== null && (
        <RepayDebtSheet
          api={api}
          branchId={branchId}
          clientId={clientId}
          membershipId={repayId}
          remaining={
            memberships.data?.memberships.find((item) => item.id === repayId)?.sessionsRemaining ??
            null
          }
          open
          onOpenChange={(open) => {
            if (!open) setRepayId(null);
          }}
        />
      )}

      <ConversationSheet api={api} clientId={clientId} open={chatOpen} onOpenChange={setChatOpen} />

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
