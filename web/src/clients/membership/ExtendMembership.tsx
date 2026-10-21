import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  type BranchId,
  type ClientId,
  type LocalDate,
  type MembershipId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { addDays } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { membershipHistoryQuery, membershipsQuery } from "../card/clientCardQueries";

/**
 * Вложенная панель «Продление абонемента» для активного незамороженного абонемента [membershipId]
 * клиента [clientId] с текущей датой окончания [endDate]: новая дата окончания и причина.
 * Дату и причину окончательно проверяет сервер.
 */
export function ExtendMembershipSheet({
  open,
  onOpenChange,
  api,
  branchId,
  clientId,
  membershipId,
  endDate,
}: {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly endDate: LocalDate;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("membership.extend.title")}>
      {open && (
        <ExtendForm
          api={api}
          branchId={branchId}
          clientId={clientId}
          membershipId={membershipId}
          endDate={endDate}
          onFinished={() => {
            onOpenChange(false);
          }}
        />
      )}
    </EditSheet>
  );
}

/** Форма продления абонемента [membershipId]; после успешного продления вызывает [onFinished]. */
function ExtendForm({
  api,
  branchId,
  clientId,
  membershipId,
  endDate,
  onFinished,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly membershipId: MembershipId;
  readonly endDate: LocalDate;
  readonly onFinished: () => void;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const minDate = addDays(endDate, 1);
  const [newEnd, setNewEnd] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const parsed = LocalDateSchema.safeParse(newEnd);
  const valid = parsed.success && parsed.data >= minDate && reason.trim() !== "";

  const submit = async () => {
    if (!parsed.success) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await api.call("memberships/extend", {
      membershipId,
      endDate: parsed.data,
      reason: reason.trim(),
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
        queryKey: membershipHistoryQuery(api, branchId, membershipId).queryKey,
      }),
    ]);
    onFinished();
  };

  return (
    <EditSheetBody>
      {error !== null && <FormAlert message={error} />}
      <p className="text-sm text-muted-foreground">
        {t("membership.extend.current", { date: format.date(endDate) })}
      </p>
      <div className="space-y-1.5">
        <Label htmlFor="extend-end">{t("membership.extend.end")}</Label>
        <Input
          id="extend-end"
          type="date"
          required
          min={minDate}
          value={newEnd}
          onChange={(event) => {
            setNewEnd(event.target.value);
          }}
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="extend-reason">{t("membership.extend.reason")}</Label>
        <Input
          id="extend-reason"
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
          disabled={pending || !valid}
          aria-busy={pending}
          onClick={() => {
            void submit();
          }}
        >
          {t("membership.extend.action")}
        </Button>
      </div>
    </EditSheetBody>
  );
}
