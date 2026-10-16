import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientId,
  MembershipEventSchema,
  MembershipHistoryEntrySchema,
  MembershipId,
  MembershipSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { membershipHistoryQuery, membershipsQuery } from "../card/clientCardQueries";
import {
  ClosePendingReasons,
  EndingMark,
  MembershipActivationNote,
  MembershipPill,
  MembershipSessions,
  MembershipSessionsBar,
} from "./MembershipParts";
import { membershipView, type MembershipView } from "./membershipView";

/**
 * Панель абонемента [membership] клиента [clientId] справа поверх карточки клиента: состояние,
 * параметры, история и действия. Панель открыта при [open] и найденном абонементе;
 * закрытие сообщается через [onOpenChange].
 */
export function MembershipSheet({
  membership,
  open,
  api,
  branchId,
  clientId,
  onOpenChange,
}: {
  /** Абонемент панели; `null` — абонемент не выбран или его нет в загруженных данных. */
  readonly membership: MembershipSchema | null;
  readonly open: boolean;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly onOpenChange: (open: boolean) => void;
}) {
  return (
    <EditSheet
      open={open && membership !== null}
      onOpenChange={onOpenChange}
      title={membership?.name ?? ""}
      size="md"
    >
      {membership !== null && (
        <MembershipPanel
          key={membership.id}
          membership={membership}
          api={api}
          branchId={branchId}
          clientId={clientId}
        />
      )}
    </EditSheet>
  );
}

/** Действие с подтверждением в панели абонемента. */
type PendingAction = "close" | "revoke";

/**
 * Содержимое панели абонемента [membership]: пометка состояния, параметры, действия,
 * доступные для состояния («Закрыть» — у активного, «Отозвать» — у неактивированного и активного),
 * и история от новых записей к старым.
 */
function MembershipPanel({
  membership,
  api,
  branchId,
  clientId,
}: {
  readonly membership: MembershipSchema;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const view = membershipView(membership);
  const historyQuery = membershipHistoryQuery(api, branchId, membership.id);
  const [confirming, setConfirming] = useState<PendingAction | null>(null);
  const [shownAction, setShownAction] = useState<PendingAction>("close");
  const [error, setError] = useState<string | null>(null);
  const canClose = membership.state.type === "active";
  const canRevoke = membership.state.type === "active" || membership.state.type === "inactive";

  const ask = (action: PendingAction) => {
    setError(null);
    setShownAction(action);
    setConfirming(action);
  };

  const perform = async (action: PendingAction) => {
    const result = await api.call(action === "close" ? "memberships/close" : "memberships/revoke", {
      membershipId: membership.id,
    });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
    }
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: membershipsQuery(api, branchId, clientId).queryKey,
      }),
      queryClient.invalidateQueries({ queryKey: historyQuery.queryKey }),
    ]);
  };

  return (
    <EditSheetBody>
      <div className="flex flex-wrap items-center gap-2">
        <MembershipPill phase={view.phase} />
        <span className="text-sm">
          <ClosePendingReasons view={view} />
        </span>
      </div>
      {error !== null && <FormAlert message={error} />}
      <MembershipParams membership={membership} view={view} />
      {(canClose || canRevoke) && (
        <div className="flex flex-wrap gap-2">
          {canClose && (
            <Button
              type="button"
              variant="outline"
              aria-label={t("clientCard.membership.closeLabel")}
              onClick={() => {
                ask("close");
              }}
            >
              {t("clientCard.membership.close")}
            </Button>
          )}
          {canRevoke && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                ask("revoke");
              }}
            >
              {t("membership.revoke")}
            </Button>
          )}
        </div>
      )}
      <MembershipHistory api={api} branchId={branchId} membershipId={membership.id} />
      <ConfirmDialog
        open={confirming !== null}
        onOpenChange={(next) => {
          if (!next) {
            setConfirming(null);
          }
        }}
        title={
          shownAction === "close"
            ? t("membership.closeConfirmTitle")
            : t("membership.revokeConfirmTitle")
        }
        description={
          shownAction === "close"
            ? t("membership.closeConfirmText")
            : t("membership.revokeConfirmText")
        }
        confirmLabel={
          shownAction === "close" ? t("clientCard.membership.close") : t("membership.revoke")
        }
        onConfirm={() => perform(shownAction)}
      />
    </EditSheetBody>
  );
}

/** Параметры абонемента [membership]: режимы, даты, занятия, стоимость; [view] — его вид. */
function MembershipParams({
  membership,
  view,
}: {
  readonly membership: MembershipSchema;
  readonly view: MembershipView;
}) {
  const { t, format } = useI18n();
  const { state } = membership;
  return (
    <dl className="divide-y rounded-lg border bg-card text-sm">
      <Field label={t("membership.issuedOn")}>{format.date(membership.issuedOn)}</Field>
      <Field label={t("clients.issue.activationMode")}>
        {t(`membership.activation.${membership.activationMode}`)}
        {view.startDate === null && (
          <span className="block text-muted-foreground">
            <MembershipActivationNote membership={membership} />
          </span>
        )}
      </Field>
      <Field label={t("clients.issue.closeMode")}>
        {t(`membership.close.${membership.closeMode}`)}
      </Field>
      <Field label={t("clientCard.membership.period")}>
        {view.startDate === null || view.endDate === null ? (
          t("membership.durationOnly", {
            duration:
              membership.durationUnit === "DAYS"
                ? t("tariffs.durationDays", { count: membership.durationValue })
                : t("tariffs.durationMonths", { count: membership.durationValue }),
          })
        ) : (
          <>
            {format.date(view.startDate)} —{" "}
            <EndingMark active={view.endingByDate}>{format.date(view.endDate)}</EndingMark>
          </>
        )}
      </Field>
      {state.type === "closed" && (
        <Field label={t("membership.closedOn")}>
          {format.date(state.closedOn)} · {t(`membership.closeReason.${state.reason}`)}
        </Field>
      )}
      <Field label={t("clientCard.membership.sessions")}>
        <EndingMark active={view.endingBySessions}>
          <MembershipSessions membership={membership} />
        </EndingMark>
        <MembershipSessionsBar membership={membership} />
      </Field>
      <Field label={t("clientCard.membership.price")}>
        <span className="tabular-nums">{format.money(membership.price)}</span>
      </Field>
    </dl>
  );
}

/** История абонемента [membershipId] от новых записей к старым. */
function MembershipHistory({
  api,
  branchId,
  membershipId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly membershipId: MembershipId;
}) {
  const { t } = useI18n();
  const history = useQuery(membershipHistoryQuery(api, branchId, membershipId));
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-medium">{t("membership.history")}</h3>
      {history.isError && <FormAlert message={t("directory.loadError")} />}
      {history.isPending && <Skeleton className="h-16 w-full" />}
      {history.data !== undefined && (
        <ol className="divide-y rounded-lg border bg-card text-sm">
          {history.data.entries.map((entry) => (
            <HistoryEntry key={entry.id} entry={entry} />
          ))}
        </ol>
      )}
    </section>
  );
}

/** Запись истории [entry]: момент, действие с данными и автор (сотрудник или «Система»). */
function HistoryEntry({ entry }: { readonly entry: MembershipHistoryEntrySchema }) {
  const { t, format } = useI18n();
  return (
    <li className="space-y-0.5 px-4 py-2.5">
      <div className="font-medium">
        <EventText event={entry.event} />
      </div>
      <div className="text-xs text-muted-foreground">
        {format.dateTime(entry.occurredAt)} · {entry.actorName ?? t("membership.history.system")}
      </div>
    </li>
  );
}

/** Действие истории [event] с его данными. */
function EventText({ event }: { readonly event: MembershipEventSchema }) {
  const { t, format } = useI18n();
  switch (event.type) {
    case "issued":
      return (
        <>
          {t("membership.history.issued", {
            activation: t(`membership.activation.${event.activationMode}`),
            close: t(`membership.close.${event.closeMode}`),
          })}
        </>
      );
    case "activated":
      return (
        <>
          {t("membership.history.activated", {
            reason: t(`membership.activationReason.${event.reason}`),
            from: format.date(event.startDate),
            to: format.date(event.endDate),
          })}
        </>
      );
    case "close_notified":
      return <>{t("membership.history.closeNotified")}</>;
    case "closed":
      return (
        <>
          {t("membership.history.closed", {
            reason: t(`membership.closeReason.${event.reason}`),
          })}
        </>
      );
    case "revoked":
      return <>{t("membership.history.revoked")}</>;
  }
}

/** Строка сведений панели: подпись [label] и значение. */
function Field({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8rem_1fr] gap-3 px-4 py-2.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}
