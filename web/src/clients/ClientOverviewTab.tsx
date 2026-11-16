import { Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientDetailResponse,
  MembershipId,
  TaskId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { ClientContactList } from "./card/ClientContactList";
import type { ClientAlert } from "./card/clientCardAlerts";
import { ClientGroupsSection } from "./ClientGroupsSection";
import { ClientMessagesSection } from "./ClientActivitySections";

export function ClientOverviewTab({
  api,
  branchId,
  client,
  quickInfo,
  alerts,
  chat,
  notes,
  onChanged,
  onChat,
  onNotes,
  onIssue,
  onBalance,
  onRepay,
  onTask,
  onTasks,
  groupOpen,
  onGroupOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly quickInfo: ReactNode;
  readonly alerts: readonly ClientAlert[];
  readonly chat: boolean;
  readonly notes: boolean;
  readonly onChanged: () => Promise<void>;
  readonly onChat: () => void;
  readonly onNotes: () => void;
  readonly onIssue: () => void;
  readonly onBalance: (() => void) | undefined;
  readonly onRepay: (id: MembershipId) => void;
  readonly onTask: (id: TaskId) => void;
  readonly onTasks: () => void;
  readonly groupOpen: boolean;
  readonly onGroupOpen: (open: boolean) => void;
}) {
  const { t, format } = useI18n();
  const latest = useQuery({
    ...apiQuery(api, branchId, "clients/notes/list", { clientId: client.id }),
    enabled: notes,
  });
  const note = latest.data?.notes.toSorted((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
  return (
    <div className="space-y-4">
      {quickInfo}
      {alerts.length > 0 && (
        <section className="rounded-lg border bg-card p-4">
          <h2 className="font-medium">{t("clientOverview.attention")}</h2>
          <ul className="divide-y">
            {alerts.map((alert) => (
              <li
                key={alert.type}
                className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"
              >
                {alert.type === "debt" && (
                  <>
                    <div className="text-destructive">
                      {alert.sessions.length > 0 && (
                        <>
                          <p>
                            {t("clientOverview.unpaidCount", { count: alert.sessions.length })} ·{" "}
                            {[...new Set(alert.sessions.map((session) => session.groupName))].join(
                              ", ",
                            )}
                          </p>
                          <p>
                            {alert.sessions.map((session) => format.date(session.date)).join(", ")}
                          </p>
                        </>
                      )}
                      {alert.amount.minorUnits < 0 && (
                        <p>{t("clientCard.alert.debt", { amount: format.money(alert.amount) })}</p>
                      )}
                      {alert.membership !== null && alert.repayLimit < alert.sessions.length && (
                        <p>
                          {alert.membership.groups.map((group) => group.name).join(", ")} ·{" "}
                          {t("clientOverview.repayLimit", {
                            count: alert.repayLimit,
                            total: alert.sessions.length,
                          })}
                        </p>
                      )}
                    </div>
                    {alert.membership !== null ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          if (alert.membership !== null) onRepay(alert.membership.id);
                        }}
                      >
                        {t(
                          alert.repayLimit < alert.sessions.length
                            ? "clientOverview.repayPart"
                            : "membership.repay.action",
                        )}
                      </Button>
                    ) : alert.sessions.length > 0 ? (
                      client.state === "ACTIVE" && (
                        <Button size="sm" variant="outline" onClick={onIssue}>
                          {t("clients.detail.issueSubscription")}
                        </Button>
                      )
                    ) : (
                      onBalance !== undefined && (
                        <Button size="sm" variant="outline" onClick={onBalance}>
                          {t("clientCard.balance")}
                        </Button>
                      )
                    )}
                  </>
                )}
                {alert.type === "noActiveSubscription" && (
                  <>
                    <span>{t("clientCard.alert.noActiveSubscription")}</span>
                    <Button size="sm" variant="outline" onClick={onIssue}>
                      {t("clients.detail.issueSubscription")}
                    </Button>
                  </>
                )}
                {alert.type === "overdueTasks" && (
                  <>
                    <span>
                      {t("clientCard.alert.overdueTasks", { count: alert.count })} ·{" "}
                      {alert.first?.title}
                    </span>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        if (alert.count === 1 && alert.first !== undefined) onTask(alert.first.id);
                        else onTasks();
                      }}
                    >
                      {t("clientOverview.open")}
                    </Button>
                  </>
                )}
                {alert.type === "birthday" && (
                  <span>{t("clientCard.alert.birthday", { date: format.date(alert.on) })}</span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
      <div className="grid gap-4 min-[1000px]:grid-cols-2">
        <div className="space-y-4">
          <ClientGroupsSection
            api={api}
            branchId={branchId}
            clientId={client.id}
            groups={client.groups}
            onChanged={onChanged}
            open={groupOpen}
            onOpenChange={onGroupOpen}
          />
          {(chat || notes) && <Contacts client={client} />}
        </div>
        <div className="space-y-4">
          {chat && (
            <ClientMessagesSection
              api={api}
              branchId={branchId}
              clientId={client.id}
              onOpen={onChat}
            />
          )}
          {notes && (
            <section className="rounded-lg border bg-card p-4">
              <h2 className="font-medium">{t("clientOverview.latestNote")}</h2>
              {note !== undefined && (
                <>
                  <p className="my-2 whitespace-pre-wrap text-sm">{note.text}</p>
                  <p className="text-xs text-muted-foreground">
                    {note.author.name} · {format.dateTime(note.createdAt)}
                  </p>
                </>
              )}
              <Button size="sm" variant="ghost" onClick={onNotes}>
                {t("clientOverview.tab.notes")} →
              </Button>
            </section>
          )}
          {!chat && !notes && <Contacts client={client} />}
        </div>
      </div>
    </div>
  );
}
function Contacts({ client }: { readonly client: ClientDetailResponse }) {
  const { t } = useI18n();
  return (
    <section className="rounded-lg border bg-card">
      <div className="flex items-center justify-between border-b p-4">
        <h2 className="font-medium">{t("clients.contacts")}</h2>
        {client.state === "ACTIVE" && (
          <Link
            className="text-sm text-primary hover:underline"
            to="/clients/$clientId/edit"
            params={{ clientId: client.id }}
          >
            {t("action.edit")}
          </Link>
        )}
      </div>
      <ClientContactList contacts={client.contacts} />
    </section>
  );
}
