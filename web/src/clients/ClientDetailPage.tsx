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
import { useActionSheet } from "@/ui/actionSheet";
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

/**
 * Панель действия поверх карточки или панели списка; одновременно открыта не больше одной.
 */
type ActionSheet =
  | { readonly kind: "membership"; readonly id: MembershipId }
  | { readonly kind: "repay"; readonly id: MembershipId }
  | { readonly kind: "task"; readonly id: TaskId }
  | { readonly kind: "taskCreate" }
  | { readonly kind: "issue" }
  | { readonly kind: "saleCreate" }
  | { readonly kind: "group" }
  | { readonly kind: "sections" }
  | { readonly kind: "chat" }
  | { readonly kind: "balance" };

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
  const canViewBalance = hasPermission(session, "CAN_VIEW_CLIENT_BALANCE");
  const [listPanel, setListPanel] = useState<"memberships" | "sessions" | "tasks" | "notes" | null>(
    null,
  );
  const { sheet, open: setSheet, closeOn } = useActionSheet<ActionSheet>();
  /** Обработчик, открывающий панель [next]. */
  const show = (next: ActionSheet) => () => {
    setSheet(next);
  };
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

  if (client.data === undefined) {
    return (
      <section className="max-w-6xl space-y-4">
        <BackLink link={{ to: "/clients" }} />
        <PageHeader title={t("clients.title")} />
        {client.isPending && <Skeleton className="h-64 w-full" />}
        {client.isError && <FormAlert message={t("clients.loadClientError")} />}
      </section>
    );
  }
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
    setSheet({ kind: "membership", id });
  };
  const openTask = (id: TaskId) => {
    setSheet({ kind: "task", id });
  };
  const detailPanels = (
    <>
      {sheet?.kind === "membership" && (
        <MembershipSheet
          membership={
            memberships.data?.memberships.find((membership) => membership.id === sheet.id) ?? null
          }
          open
          api={api}
          branchId={branchId}
          clientId={clientId}
          onOpenChange={closeOn("membership")}
        />
      )}

      {sheet?.kind === "taskCreate" && (
        <TaskCreateSheet
          api={api}
          open
          onOpenChange={closeOn("taskCreate")}
          client={{ id: data.id, name: data.name }}
        />
      )}
      {sheet?.kind === "task" && (
        <TaskSheet
          api={api}
          taskId={sheet.id}
          onClose={() => {
            closeOn("task")(false);
          }}
        />
      )}

      {sheet?.kind === "issue" && (
        <IssueSubscriptionSheet
          api={api}
          branchId={branchId}
          clientId={clientId}
          open
          onOpenChange={closeOn("issue")}
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
        onTaskCreate={flags?.tasks === true ? show({ kind: "taskCreate" }) : undefined}
        onAddGroup={show({ kind: "group" })}
        onSections={show({ kind: "sections" })}
        onToggleArchived={(archived) => {
          void toggleArchived(archived);
        }}
        onIssueSubscription={show({ kind: "issue" })}
        onOpenChat={show({ kind: "chat" })}
      />
      {(sections.isError || unpaid.isError) && <FormAlert message={t("clients.loadClientError")} />}
      {data.state === "ACTIVE" && (
        <Button type="button" variant="outline" onClick={show({ kind: "saleCreate" })}>
          {t("finance.sellService")}
        </Button>
      )}
      <SaleCreateSheet
        api={api}
        branchId={branchId}
        client={data}
        open={sheet?.kind === "saleCreate"}
        onOpenChange={closeOn("saleCreate")}
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
        onChat={show({ kind: "chat" })}
        onNotes={() => {
          setListPanel("notes");
        }}
        onIssue={show({ kind: "issue" })}
        onBalance={canViewBalance ? show({ kind: "balance" }) : undefined}
        onRepay={(id) => {
          setSheet({ kind: "repay", id });
        }}
        onTask={openTask}
        onTasks={() => {
          setListPanel("tasks");
        }}
        groupOpen={sheet?.kind === "group"}
        onGroupOpen={(open) => {
          if (open) {
            setSheet({ kind: "group" });
          } else {
            closeOn("group")(false);
          }
        }}
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
              onCreate={show({ kind: "taskCreate" })}
              onOpen={openTask}
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
            onBalanceClick={canViewBalance ? show({ kind: "balance" }) : undefined}
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
          if (!open) {
            setListPanel(null);
          }
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
              onIssue={show({ kind: "issue" })}
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
              onCreate={show({ kind: "taskCreate" })}
              onOpen={openTask}
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
      <ClientCardSectionsSheet
        api={api}
        open={sheet?.kind === "sections"}
        onOpenChange={closeOn("sections")}
      />
      {sheet?.kind === "repay" && (
        <RepayDebtSheet
          api={api}
          branchId={branchId}
          clientId={clientId}
          membershipId={sheet.id}
          remaining={
            memberships.data?.memberships.find((item) => item.id === sheet.id)?.sessionsRemaining ??
            null
          }
          open
          onOpenChange={closeOn("repay")}
        />
      )}

      <ConversationSheet
        api={api}
        clientId={clientId}
        open={sheet?.kind === "chat"}
        onOpenChange={closeOn("chat")}
      />

      {canViewBalance && (
        <ClientBalanceSheet
          api={api}
          branchId={branchId}
          client={data}
          open={sheet?.kind === "balance"}
          onOpenChange={closeOn("balance")}
          onChanged={(updated) => {
            queryClient.setQueryData(detailQuery.queryKey, updated);
          }}
        />
      )}
    </section>
  );
}
