import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientId,
  MembershipId,
  SessionId,
  UnpaidSessionSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import {
  membershipHistoryQuery,
  membershipsQuery,
  unpaidSessionsQuery,
} from "../card/clientCardQueries";

/**
 * Выбор неоплаченных занятий [sessions] для погашения с абонемента [membershipId] клиента
 * [clientId]: все отмечены по умолчанию; больше остатка [remaining] (`null` — безлимит) выбрать
 * нельзя — подтверждение недоступно с объяснением. После погашения обновляет абонементы, историю
 * и неоплаченные занятия и вызывает [onFinished]; кнопка [skipLabel] тоже вызывает [onFinished]
 * без списания. Ошибка сервера показывается здесь же.
 */
export function RepayDebtChoice({
  api,
  branchId,
  clientId,
  membershipId,
  remaining,
  sessions,
  skipLabel,
  onFinished,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly remaining: number | null;
  readonly sessions: readonly UnpaidSessionSchema[];
  readonly skipLabel: string;
  readonly onFinished: () => void;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<readonly SessionId[]>(() =>
    sessions.map((session) => session.sessionId),
  );
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const tooMany = remaining !== null && selected.length > remaining;

  const repay = async () => {
    setPending(true);
    setError(null);
    const result = await api.call("memberships/repay-debt", { membershipId, sessionIds: selected });
    setPending(false);
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: membershipsQuery(api, branchId, clientId).queryKey,
      }),
      queryClient.invalidateQueries({
        queryKey: membershipHistoryQuery(api, branchId, membershipId).queryKey,
      }),
      queryClient.invalidateQueries({
        queryKey: unpaidSessionsQuery(api, branchId, membershipId).queryKey,
      }),
    ]);
    onFinished();
  };

  return (
    <div className="space-y-3">
      {error !== null && <FormAlert message={error} />}
      <fieldset className="space-y-1">
        <legend className="text-sm font-medium">{t("membership.repay.sessions")}</legend>
        {sessions.map((session) => (
          <label key={session.sessionId} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={selected.includes(session.sessionId)}
              onChange={(event) => {
                setSelected(
                  event.target.checked
                    ? [...selected, session.sessionId]
                    : selected.filter((id) => id !== session.sessionId),
                );
              }}
              className="size-4 accent-primary"
            />
            {format.date(session.date)} · {session.startTime.slice(0, 5)} · {session.groupName}
          </label>
        ))}
      </fieldset>
      {tooMany && (
        <p role="status" className="text-xs text-destructive">
          {t("membership.repay.tooMany", { count: remaining })}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          disabled={pending || selected.length === 0 || tooMany}
          aria-busy={pending}
          onClick={() => {
            void repay();
          }}
        >
          {t("membership.repay.submit", { count: selected.length })}
        </Button>
        <Button type="button" variant="outline" disabled={pending} onClick={onFinished}>
          {skipLabel}
        </Button>
      </div>
    </div>
  );
}

/**
 * Вложенная панель «Погасить долг» для активного абонемента [membershipId] клиента [clientId]
 * с остатком [remaining]: неоплаченные занятия в группах абонемента, все отмечены.
 */
export function RepayDebtSheet({
  open,
  onOpenChange,
  api,
  branchId,
  clientId,
  membershipId,
  remaining,
}: {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly remaining: number | null;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("membership.repay.title")}>
      {open && (
        <RepayDebtBody
          api={api}
          branchId={branchId}
          clientId={clientId}
          membershipId={membershipId}
          remaining={remaining}
          onFinished={() => {
            onOpenChange(false);
          }}
        />
      )}
    </EditSheet>
  );
}

/** Загружает неоплаченные занятия абонемента и показывает выбор для погашения. */
function RepayDebtBody({
  api,
  branchId,
  clientId,
  membershipId,
  remaining,
  onFinished,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly remaining: number | null;
  readonly onFinished: () => void;
}) {
  const { t } = useI18n();
  const unpaid = useQuery(unpaidSessionsQuery(api, branchId, membershipId));
  return (
    <EditSheetBody>
      {unpaid.isError && <FormAlert message={t("directory.loadError")} />}
      {unpaid.isPending && <Skeleton className="h-24 w-full" />}
      {unpaid.data !== undefined &&
        (unpaid.data.sessions.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("membership.repay.empty")}</p>
        ) : (
          <RepayDebtChoice
            api={api}
            branchId={branchId}
            clientId={clientId}
            membershipId={membershipId}
            remaining={remaining}
            sessions={unpaid.data.sessions}
            skipLabel={t("action.cancel")}
            onFinished={onFinished}
          />
        ))}
    </EditSheetBody>
  );
}
