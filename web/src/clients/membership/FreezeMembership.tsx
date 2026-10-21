import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  type BranchId,
  type ClientId,
  type MembershipSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { membershipHistoryQuery, membershipsQuery } from "../card/clientCardQueries";

/**
 * Вложенная панель «Заморозка абонемента» для активного абонемента [membership] клиента
 * [clientId]: первый и последний день, причина. Предупреждает о других активных абонементах
 * клиента в те же группы — они не замораживаются. Даты и причину проверяет сервер.
 */
export function FreezeMembershipSheet({
  open,
  onOpenChange,
  api,
  branchId,
  clientId,
  membership,
}: {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membership: MembershipSchema;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("membership.freeze.title")}>
      {open && (
        <FreezeForm
          api={api}
          branchId={branchId}
          clientId={clientId}
          membership={membership}
          onFinished={() => {
            onOpenChange(false);
          }}
        />
      )}
    </EditSheet>
  );
}

/** Форма заморозки абонемента [membership]; после успешной заморозки вызывает [onFinished]. */
function FreezeForm({
  api,
  branchId,
  clientId,
  membership,
  onFinished,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membership: MembershipSchema;
  readonly onFinished: () => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const memberships = useQuery(membershipsQuery(api, branchId, clientId));
  const [start, setStart] = useState<string>(todayLocalDate());
  const [end, setEnd] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const startDate = LocalDateSchema.safeParse(start);
  const endDate = LocalDateSchema.safeParse(end);
  const groupIds = new Set(membership.groups.map((group) => group.id));
  const others = (memberships.data?.memberships ?? []).filter(
    (other) =>
      other.id !== membership.id &&
      other.state.type === "active" &&
      other.groups.some((group) => groupIds.has(group.id)),
  );

  const submit = async () => {
    if (!startDate.success || !endDate.success) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await api.call("memberships/freeze", {
      membershipId: membership.id,
      startDate: startDate.data,
      endDate: endDate.data,
      reason,
    });
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
        queryKey: membershipHistoryQuery(api, branchId, membership.id).queryKey,
      }),
    ]);
    onFinished();
  };

  return (
    <EditSheetBody>
      {error !== null && <FormAlert message={error} />}
      <p className="text-sm text-muted-foreground">{t("membership.freeze.hint")}</p>
      {others.length > 0 && (
        <p role="status" className="text-sm text-amber-700 dark:text-amber-400">
          {t("membership.freeze.otherMemberships", {
            names: others.map((other) => other.name).join(", "),
          })}
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="freeze-start">{t("membership.freeze.start")}</Label>
          <Input
            id="freeze-start"
            type="date"
            required
            min={todayLocalDate()}
            value={start}
            onChange={(event) => {
              setStart(event.target.value);
            }}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="freeze-end">{t("membership.freeze.end")}</Label>
          <Input
            id="freeze-end"
            type="date"
            required
            min={start}
            value={end}
            onChange={(event) => {
              setEnd(event.target.value);
            }}
          />
        </div>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="freeze-reason">{t("membership.freeze.reason")}</Label>
        <Input
          id="freeze-reason"
          required
          autoComplete="off"
          value={reason}
          onChange={(event) => {
            setReason(event.target.value);
          }}
        />
      </div>
      <div>
        <Button
          type="button"
          disabled={pending || !startDate.success || !endDate.success || reason.trim() === ""}
          aria-busy={pending}
          onClick={() => {
            void submit();
          }}
        >
          {t("membership.freeze.action")}
        </Button>
      </div>
    </EditSheetBody>
  );
}
