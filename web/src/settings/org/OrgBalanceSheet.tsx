import { useForm } from "@tanstack/react-form";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type { Currency, OrgBalanceJournalEntry } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { CURRENCIES } from "@/lib/currency";
import { parseMoney } from "@/lib/money";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";

/** Описание платежа пополнения — то же значение, что шлёт KMP-клиент, для единого журнала. */
const REPLENISH_DESCRIPTION = "Пополнение баланса";

/** Ключ подписи типа операции журнала баланса организации. */
function operationLabelKey(operationType: string): PlainMessageKey {
  switch (operationType) {
    case "admin_credit":
      return "orgBalance.operation.admin_credit";
    case "admin_debit":
      return "orgBalance.operation.admin_debit";
    case "system_fee":
      return "orgBalance.operation.system_fee";
    case "replenishment":
      return "orgBalance.operation.replenishment";
    case "bonus":
      return "orgBalance.operation.bonus";
    default:
      return "orgBalance.operation.replenishment";
  }
}

/** Панель баланса организации, открытая при [open]. */
export function OrgBalanceSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("orgBalance.title")} size="lg">
      <OrgBalancePanel api={api} />
    </EditSheet>
  );
}

/**
 * Баланс организации и его история (паритет с `OrgBalanceScreen` KMP-клиента): пополнение
 * открывается панелью поверх, запускает оплату через ЮKassa и переводит браузер на её
 * страницу; после оплаты ЮKassa возвращает пользователя на страницу результата.
 */
function OrgBalancePanel({ api }: { readonly api: ApiClient }) {
  const { t, format } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const detail = useQuery(apiQuery(api, branchId, "org-balance/detail"));
  const settings = useQuery(apiQuery(api, branchId, "org/settings"));
  const [replenishOpen, setReplenishOpen] = useState(false);

  return (
    <EditSheetBody>
      {detail.isError && <FormAlert message={t("orgBalance.loadError")} />}
      {detail.isPending && <Skeleton className="h-24 w-full" />}
      {detail.data !== undefined && (
        <div className="flex items-center justify-between gap-3 rounded-lg border bg-card p-4">
          <div>
            <p className="text-sm text-muted-foreground">{t("orgBalance.total")}</p>
            <p className="text-2xl font-semibold tabular-nums">
              {format.money(detail.data.totalAmount)}
            </p>
          </div>
          <Button
            disabled={settings.data === undefined}
            onClick={() => {
              setReplenishOpen(true);
            }}
          >
            {t("orgBalance.replenish")}
          </Button>
        </div>
      )}
      <h2 className="pt-2 text-base font-semibold">{t("orgBalance.historyTitle")}</h2>
      {detail.data?.history.length === 0 && (
        <p className="py-8 text-center text-muted-foreground">{t("orgBalance.historyEmpty")}</p>
      )}
      {detail.data !== undefined && detail.data.history.length > 0 && (
        <ul className="divide-y rounded-lg border bg-card">
          {detail.data.history.map((entry) => (
            <BalanceHistoryRow key={entry.id} entry={entry} />
          ))}
        </ul>
      )}
      <EditSheet
        open={replenishOpen && settings.data !== undefined}
        onOpenChange={setReplenishOpen}
        title={t("orgBalance.replenishTitle")}
      >
        {settings.data !== undefined && (
          <ReplenishForm api={api} currency={settings.data.currency} />
        )}
      </EditSheet>
    </EditSheetBody>
  );
}

/** Строка истории операций по балансу организации. */
function BalanceHistoryRow({ entry }: { readonly entry: OrgBalanceJournalEntry }) {
  const { t, format } = useI18n();
  return (
    <li className="flex items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <p className="font-medium">{t(operationLabelKey(entry.operationType))}</p>
        <p className="truncate text-sm text-muted-foreground">{entry.description}</p>
        <time dateTime={entry.createdAt} className="text-xs text-muted-foreground">
          {format.dateTime(entry.createdAt)}
        </time>
      </div>
      <span
        className={`shrink-0 font-medium tabular-nums ${entry.amount.minorUnits < 0 ? "text-destructive" : "text-primary"}`}
      >
        {format.money(entry.amount)}
      </span>
    </li>
  );
}

/** Форма пополнения баланса: сумма в валюте организации, запускает оплату ЮKassa. */
function ReplenishForm({
  api,
  currency,
}: {
  readonly api: ApiClient;
  readonly currency: Currency;
}) {
  const { t } = useI18n();
  const [failure, setFailure] = useState<string | null>(null);
  const symbol = CURRENCIES.find((c) => c.code === currency)?.symbol ?? currency;
  const schema = z.object({
    amount: z.string().refine((v) => {
      const money = parseMoney(v, currency);
      return money !== null && money.minorUnits > 0;
    }, t("error.invalidAmount")),
  });
  const form = useForm({
    defaultValues: { amount: "" },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const money = parseMoney(value.amount, currency);
      if (money === null) {
        return;
      }
      setFailure(null);
      const result = await api.call("payments/initiate", {
        amount: money.minorUnits,
        description: REPLENISH_DESCRIPTION,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      window.location.assign(result.value.confirmationUrl);
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
          submitLabel={t("orgBalance.replenish")}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="amount">
            {(field) => (
              <TextField
                field={field}
                label={t("orgBalance.amount", { currency: symbol })}
                inputMode="decimal"
                autoComplete="off"
                required
                autoFocus
              />
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
