import { Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import {
  CakeIcon,
  ListChecksIcon,
  PencilIcon,
  TicketIcon,
  TriangleAlertIcon,
  WalletIcon,
} from "lucide-react";
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
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { ClientCardBlock } from "./card/ClientCardBlock";
import { ClientContactList } from "./card/ClientContactList";
import type { ClientAlert } from "./card/clientCardAlerts";
import { ClientGroupsSection } from "./ClientGroupsSection";
import { ClientMessagesSection } from "./ClientActivitySections";

export function ClientOverviewTab({
  api,
  branchId,
  client,
  quickInfo,
  profile,
  tasks,
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
  readonly profile: ReactNode;
  readonly tasks: ReactNode;
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
  const latestNotes = (latest.data?.notes ?? [])
    .toSorted((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 2);
  const extras = chat || notes || tasks !== null;
  return (
    <div className="space-y-4">
      {quickInfo}
      {alerts.length > 0 && (
        <section className="rounded-xl border bg-card shadow-xs">
          <h2 className="flex items-center gap-2.5 border-b px-4 py-3 font-semibold">
            <span className="inline-flex size-7 items-center justify-center rounded-lg bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300 [&_svg]:size-4">
              <TriangleAlertIcon aria-hidden />
            </span>
            {t("clientOverview.attention")}
            <span aria-hidden className="font-medium text-muted-foreground tabular-nums">
              {alerts.length}
            </span>
          </h2>
          <ul className="divide-y">
            {alerts.map((alert) => (
              <li
                key={alert.type}
                className="flex flex-wrap items-center gap-x-3.5 gap-y-2 px-4 py-3 text-sm [&>:nth-child(2)]:min-w-0 [&>:nth-child(2)]:flex-[1_1_260px]"
              >
                <AlertIcon type={alert.type} />
                {alert.type === "debt" && (
                  <>
                    <div>
                      {alert.sessions.length > 0 && (
                        <>
                          <p className="font-semibold">
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
          <Contacts client={client} />
          {extras && profile}
        </div>
        <div className="space-y-4">
          {!extras && profile}
          {tasks}
          {chat && (
            <ClientMessagesSection
              api={api}
              branchId={branchId}
              clientId={client.id}
              onOpen={onChat}
            />
          )}
          {notes && (
            <ClientCardBlock
              title={t("clients.detail.notes")}
              count={latest.data?.notes.length ?? 0}
              footer={
                <Button size="sm" variant="link" onClick={onNotes}>
                  {t("clientOverview.allNotes")} · {latest.data?.notes.length ?? 0}
                </Button>
              }
              actions={
                <Button size="sm" variant="link" onClick={onNotes}>
                  {t("action.add")}
                </Button>
              }
            >
              {latestNotes.length === 0 ? (
                <p className="px-4 py-3 text-sm text-muted-foreground">
                  {t("clientOverview.noNotes")}
                </p>
              ) : (
                latestNotes.map((note) => (
                  <div key={note.id} className="border-b last:border-0 px-4 py-3.5 text-sm">
                    <p className="break-words whitespace-pre-wrap">{note.text}</p>
                    <p className="mt-1.5 text-xs text-muted-foreground">
                      {note.author.name} · {format.dateTime(note.createdAt)}
                    </p>
                  </div>
                ))
              )}
            </ClientCardBlock>
          )}
        </div>
      </div>
    </div>
  );
}
/** Иконка строки «Требует внимания» по типу предупреждения [type]; цвет — по важности. */
function AlertIcon({ type }: { readonly type: ClientAlert["type"] }) {
  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex size-7.5 shrink-0 items-center justify-center rounded-lg [&_svg]:size-4",
        (type === "debt" || type === "overdueTasks") &&
          "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
        type === "noActiveSubscription" &&
          "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
        type === "birthday" && "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
      )}
    >
      {type === "debt" && <WalletIcon />}
      {type === "noActiveSubscription" && <TicketIcon />}
      {type === "overdueTasks" && <ListChecksIcon />}
      {type === "birthday" && <CakeIcon />}
    </span>
  );
}

/** Карточка «Контакты» клиента [client] со ссылкой на редактирование анкеты. */
function Contacts({ client }: { readonly client: ClientDetailResponse }) {
  const { t } = useI18n();
  return (
    <ClientCardBlock
      title={t("clients.contacts")}
      count={null}
      actions={
        client.state === "ACTIVE" && (
          <Button size="sm" variant="link" asChild>
            <Link to="/clients/$clientId/edit" params={{ clientId: client.id }}>
              <PencilIcon aria-hidden />
              {t("action.edit")}
            </Link>
          </Button>
        )
      }
    >
      <ClientContactList contacts={client.contacts} />
    </ClientCardBlock>
  );
}
