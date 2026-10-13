import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PlusIcon, WalletIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientDetailResponse, ClientId, Money } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n, type PlainMessageKey } from "@/i18n/context";
import { parseMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  ShowAllButton,
} from "./card/ClientCardBlock";

/** Направление корректировки баланса. */
type Direction = "credit" | "debit";

/** Значения формы корректировки баланса. */
interface AdjustBalanceFormValues {
  readonly direction: Direction;
  readonly amount: string;
  readonly note: string;
}

/** Схема формы корректировки баланса с сообщениями на языке [t] в валюте [currency]. */
function adjustBalanceSchema(t: I18n["t"], currency: Money["currency"]) {
  return z.object({
    direction: z.enum(["credit", "debit"]),
    amount: z
      .string()
      .refine((v) => (parseMoney(v, currency)?.minorUnits ?? 0) > 0, t("error.invalidAmount")),
    note: z.string().trim().min(1, t("error.required")),
  });
}

/** Ключ подписи типа операции [operationType] журнала баланса клиента. */
function operationLabelKey(operationType: string): PlainMessageKey {
  switch (operationType) {
    case "sale_overpayment":
      return "clientCard.operation.sale_overpayment";
    case "sale_payment":
      return "clientCard.operation.sale_payment";
    case "refund":
      return "clientCard.operation.refund";
    case "admin_credit":
      return "clientCard.operation.admin_credit";
    case "admin_debit":
      return "clientCard.operation.admin_debit";
    default:
      return "clientCard.operation.other";
  }
}

/**
 * Блок платежей карточки клиента: операции по счёту от новых к старым и административная
 * корректировка баланса. Показывается, только если у сотрудника есть право
 * `CAN_VIEW_CLIENT_BALANCE` (проверка — в вызывающем компоненте, у которого есть сессия).
 */
export function ClientBalanceSection({
  api,
  branchId,
  clientId,
  balance,
  limit,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly balance: Money;
  readonly limit: number;
  readonly onChanged: (client: ClientDetailResponse) => void;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const historyQuery = apiQuery(api, branchId, "clients/balance/history", { id: clientId });
  const history = useQuery(historyQuery);
  const entries = history.data?.entries ?? [];
  const shown = showAll ? entries : entries.slice(0, limit);

  return (
    <>
      <ClientCardBlock
        icon={<WalletIcon aria-hidden />}
        title={t("clientCard.block.payments")}
        count={entries.length}
        actions={
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t("clients.detail.balanceAdjust")}
            title={t("clients.detail.balanceAdjust")}
            onClick={() => {
              setAdjustOpen(true);
            }}
          >
            <PlusIcon aria-hidden />
          </Button>
        }
        footer={
          <ShowAllButton
            total={entries.length}
            limit={limit}
            showAll={showAll}
            onToggle={() => {
              setShowAll(!showAll);
            }}
          />
        }
      >
        <BlockStatus
          isPending={history.isPending}
          isError={history.isError}
          isEmpty={entries.length === 0}
          empty={t("clients.detail.balanceHistoryEmpty")}
        />
        {shown.length > 0 && (
          <ul className="divide-y">
            {shown.map((entry) => (
              <BlockRow
                key={entry.id}
                aside={
                  <div>
                    <div
                      className={cn(
                        "font-medium tabular-nums",
                        entry.amount.minorUnits < 0
                          ? "text-destructive"
                          : "text-emerald-700 dark:text-emerald-400",
                      )}
                    >
                      {entry.amount.minorUnits > 0 ? "+" : ""}
                      {format.money(entry.amount)}
                    </div>
                    <BlockSub>
                      {t("clientCard.balanceAfter", { amount: format.money(entry.balanceAfter) })}
                    </BlockSub>
                  </div>
                }
              >
                <div>{t(operationLabelKey(entry.operationType))}</div>
                {entry.note !== null && entry.note !== "" && <div>{entry.note}</div>}
                <BlockSub>
                  {[format.dateTime(entry.createdAt), entry.performedBy?.name]
                    .filter((part) => part !== undefined && part !== "")
                    .join(" · ")}
                </BlockSub>
              </BlockRow>
            ))}
          </ul>
        )}
      </ClientCardBlock>

      {adjustOpen && (
        <AdjustBalanceDialog
          api={api}
          clientId={clientId}
          currency={balance.currency}
          onSuccess={(client) => {
            setAdjustOpen(false);
            onChanged(client);
            void queryClient.invalidateQueries({ queryKey: historyQuery.queryKey });
          }}
          onClose={() => {
            setAdjustOpen(false);
          }}
        />
      )}
    </>
  );
}

/** Диалог административной корректировки баланса: направление, сумма и комментарий. */
function AdjustBalanceDialog({
  api,
  clientId,
  currency,
  onSuccess,
  onClose,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
  readonly currency: Money["currency"];
  readonly onSuccess: (client: ClientDetailResponse) => void;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const schema = useMemo(() => adjustBalanceSchema(t, currency), [t, currency]);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: AdjustBalanceFormValues = { direction: "credit", amount: "", note: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = parseMoney(value.amount, currency);
      if (parsed === null) {
        return;
      }
      const amount: Money = {
        currency,
        minorUnits: value.direction === "debit" ? -parsed.minorUnits : parsed.minorUnits,
      };
      setFailure(null);
      const result = await api.call("clients/balance/adjust", {
        clientId,
        amount,
        note: value.note.trim(),
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      onSuccess(result.value);
    },
  });

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent aria-describedby={undefined} closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("clients.detail.balanceAdjustTitle")}</DialogTitle>
        </DialogHeader>
        <form
          method="post"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
          className="space-y-4"
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="direction">
            {(field) => (
              <RadioGroupField
                field={field}
                label={t("clients.detail.balanceDirection")}
                options={[
                  { value: "credit", label: t("clients.detail.balanceCredit") },
                  { value: "debit", label: t("clients.detail.balanceDebit") },
                ]}
              />
            )}
          </form.Field>
          <form.Field name="amount">
            {(field) => (
              <TextField
                field={field}
                label={t("clients.detail.balanceAmount")}
                inputMode="decimal"
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <form.Field name="note">
            {(field) => (
              <TextField
                field={field}
                label={t("clients.detail.balanceNote")}
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <DialogFooter closeLabel={t("action.cancel")}>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" disabled={submitting} aria-busy={submitting}>
                  {t("action.save")}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
