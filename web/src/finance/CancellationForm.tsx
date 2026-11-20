import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { LedgerTransactionId, SaleId } from "@/api/generated/contracts";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

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
  const [reason, setReason] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <>
      <EditSheetForm
        dirty={reason !== ""}
        submitting={confirming}
        submitLabel={t("finance.cancelOperation")}
        onSubmit={() => {
          setInvalid(true);
          if (reason.trim() !== "") setConfirming(true);
        }}
      >
        {error !== null && <FormAlert message={error} />}
        <label className="block space-y-1">
          <span>{t("finance.reason")}</span>
          <Input
            value={reason}
            aria-invalid={invalid && reason.trim() === ""}
            onChange={(e) => {
              setReason(e.target.value);
            }}
          />
          {invalid && reason.trim() === "" && (
            <span className="text-sm text-destructive">{t("error.required")}</span>
          )}
        </label>
      </EditSheetForm>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("finance.cancelOperation")}
        description={reason}
        confirmLabel={t("finance.confirm")}
        onConfirm={async () => {
          const result =
            operation.kind === "sale"
              ? await api.call("sales/cancel", { saleId: operation.id, reason: reason.trim() })
              : operation.kind === "payment"
                ? await api.call("finance/payments/reverse", {
                    transactionId: operation.id,
                    reason: reason.trim(),
                  })
                : await api.call("finance/operations/reverse", {
                    transactionId: operation.id,
                    reason: reason.trim(),
                  });
          if (!result.ok) {
            setError(apiErrorMessage(t, result.error));
            return;
          }
          await onSaved();
          close();
        }}
      />
    </>
  );
}
