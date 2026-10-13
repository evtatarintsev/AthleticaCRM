import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type {
  BalanceJournalEntry,
  BranchId,
  ClientDetailResponse,
  Money,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n, type PlainMessageKey } from "@/i18n/context";
import { parseMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

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
 * Панель «Баланс» клиента [client], открытая при [open]: текущая сумма, корректировка
 * во вложенной панели и полный журнал операций по счёту от новых к старым. После корректировки
 * передаёт обновлённого клиента в [onChanged]. Право `CAN_VIEW_CLIENT_BALANCE` проверяет
 * вызывающий компонент.
 */
export function ClientBalanceSheet({
  api,
  branchId,
  client,
  open,
  onOpenChange,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly onChanged: (client: ClientDetailResponse) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("clientCard.balance")} size="lg">
      <ClientBalancePanel api={api} branchId={branchId} client={client} onChanged={onChanged} />
    </EditSheet>
  );
}

/** Содержимое панели «Баланс»: сводка, журнал и вложенная панель корректировки. */
function ClientBalancePanel({
  api,
  branchId,
  client,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly onChanged: (client: ClientDetailResponse) => void;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const [adjustOpen, setAdjustOpen] = useState(false);
  const historyQuery = apiQuery(api, branchId, "clients/balance/history", { id: client.id });
  const history = useQuery(historyQuery);
  const entries = history.data?.entries ?? [];

  return (
    <EditSheetBody>
      <div className="flex items-center justify-between gap-3 rounded-lg border bg-card p-4">
        <p
          className={cn(
            "text-2xl font-semibold tabular-nums",
            client.balance.minorUnits < 0 && "text-destructive",
          )}
        >
          {format.money(client.balance)}
        </p>
        {client.state === "ACTIVE" && (
          <Button
            onClick={() => {
              setAdjustOpen(true);
            }}
          >
            {t("clients.detail.balanceAdjust")}
          </Button>
        )}
      </div>
      <h2 className="pt-2 text-base font-semibold">{t("clientCard.balanceHistory")}</h2>
      {history.isError && <FormAlert message={t("clientCard.balanceHistoryLoadError")} />}
      {history.isPending && <Skeleton className="h-24 w-full" />}
      {history.isSuccess && entries.length === 0 && (
        <p className="py-8 text-center text-muted-foreground">
          {t("clients.detail.balanceHistoryEmpty")}
        </p>
      )}
      {entries.length > 0 && (
        <ul className="divide-y rounded-lg border bg-card">
          {entries.map((entry) => (
            <BalanceHistoryRow key={entry.id} entry={entry} />
          ))}
        </ul>
      )}
      <EditSheet
        open={adjustOpen}
        onOpenChange={setAdjustOpen}
        title={t("clients.detail.balanceAdjustTitle")}
      >
        <AdjustBalanceForm
          api={api}
          client={client}
          onSuccess={(updated) => {
            onChanged(updated);
            void queryClient.invalidateQueries({ queryKey: historyQuery.queryKey });
          }}
        />
      </EditSheet>
    </EditSheetBody>
  );
}

/** Строка журнала баланса клиента: операция, комментарий, когда и кем, сумма и остаток. */
function BalanceHistoryRow({ entry }: { readonly entry: BalanceJournalEntry }) {
  const { t, format } = useI18n();
  return (
    <li className="flex items-start justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <p className="font-medium">{t(operationLabelKey(entry.operationType))}</p>
        {entry.note !== null && entry.note !== "" && (
          <p className="text-sm break-words text-muted-foreground">{entry.note}</p>
        )}
        <p className="text-xs text-muted-foreground">
          <time dateTime={entry.createdAt}>{format.dateTime(entry.createdAt)}</time>
          {entry.performedBy !== null && ` · ${entry.performedBy.name}`}
        </p>
      </div>
      <div className="shrink-0 text-right">
        <p
          className={cn(
            "font-medium tabular-nums",
            entry.amount.minorUnits < 0
              ? "text-destructive"
              : "text-emerald-700 dark:text-emerald-400",
          )}
        >
          {entry.amount.minorUnits > 0 ? "+" : ""}
          {format.money(entry.amount)}
        </p>
        <p className="text-xs text-muted-foreground tabular-nums">
          {t("clientCard.balanceAfter", { amount: format.money(entry.balanceAfter) })}
        </p>
      </div>
    </li>
  );
}

/**
 * Форма административной корректировки баланса клиента [client]: направление, сумма
 * и комментарий. После успеха передаёт обновлённого клиента в [onSuccess] и закрывает свою панель.
 */
function AdjustBalanceForm({
  api,
  client,
  onSuccess,
}: {
  readonly api: ApiClient;
  readonly client: ClientDetailResponse;
  readonly onSuccess: (client: ClientDetailResponse) => void;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const currency = client.balance.currency;
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
        clientId: client.id,
        amount,
        note: value.note.trim(),
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      onSuccess(result.value);
      close();
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
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
                autoFocus
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
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
