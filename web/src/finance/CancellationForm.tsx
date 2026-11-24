import { useForm } from "@tanstack/react-form";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { LedgerTransactionId, SaleId } from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { requiredText } from "@/forms/requiredText";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Что отменяется: продажа, платёж клиента или кассовая операция. */
type Cancellation =
  | { readonly kind: "sale"; readonly id: SaleId }
  | { readonly kind: "payment" | "cash"; readonly id: LedgerTransactionId };

/** Обязательная причина и отдельное подтверждение отмены; отказ оставляет форму открытой. */
export function CancellationForm({
  api,
  operation,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly operation: Cancellation;
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const form = useForm({
    defaultValues: { reason: "" },
    onSubmit: () => {
      setConfirming(true);
    },
  });
  const cancel = async () => {
    const reason = form.state.values.reason.trim();
    const result =
      operation.kind === "sale"
        ? await api.call("sales/cancel", { saleId: operation.id, reason })
        : operation.kind === "payment"
          ? await api.call("finance/payments/reverse", { transactionId: operation.id, reason })
          : await api.call("finance/operations/reverse", { transactionId: operation.id, reason });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await onSaved();
    close();
  };
  return (
    <>
      <form.Subscribe selector={(state) => state.isDirty}>
        {(dirty) => (
          <EditSheetForm
            dirty={dirty}
            submitting={confirming}
            submitLabel={t("finance.cancelOperation")}
            onSubmit={() => {
              void form.handleSubmit();
            }}
          >
            {error !== null && <FormAlert message={error} />}
            <form.Field name="reason" validators={{ onSubmit: requiredText(t) }}>
              {(field) => (
                <TextField field={field} label={t("finance.reason")} autoComplete="off" />
              )}
            </form.Field>
          </EditSheetForm>
        )}
      </form.Subscribe>
      <form.Subscribe selector={(state) => state.values.reason}>
        {(reason) => (
          <ConfirmDialog
            open={confirming}
            onOpenChange={setConfirming}
            title={t("finance.cancelOperation")}
            description={reason}
            confirmLabel={t("finance.confirm")}
            onConfirm={cancel}
          />
        )}
      </form.Subscribe>
    </>
  );
}
