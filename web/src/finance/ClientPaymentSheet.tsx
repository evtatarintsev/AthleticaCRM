import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient, ApiResult } from "@/api/client";
import {
  CashAccountIdSchema,
  LocalDateSchema,
  type BranchId,
  type ClientId,
  type ClientLedgerResponse,
  type SaleId,
} from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { SelectField, TextField } from "@/forms/fields";
import { requiredText } from "@/forms/requiredText";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { moneyEditText, parseMoney } from "@/lib/money";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { PaymentBlock } from "./PaymentBlock";
import { initialPayment, paymentInput, type PaymentDraft } from "./payment";

export type ClientPaymentMode = "payment" | "payout" | "adjust";

/** Команда клиента во вложенной панели баланса или продажи. */
export function ClientPaymentSheet(props: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly summary: ClientLedgerResponse;
  readonly mode: ClientPaymentMode;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly onSaved: () => Promise<void> | void;
  readonly saleId?: SaleId;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={props.open}
      onOpenChange={props.onOpenChange}
      title={t(`finance.${props.mode}`)}
    >
      <ClientPaymentForm {...props} />
    </EditSheet>
  );
}

/** Значения формы команды клиента; суммы и дата вводятся текстом. */
interface ClientPaymentValues {
  /** Продажа, которую оплачивают, или `advance` — пополнение аванса. */
  readonly destination: string;
  /** Черновик оплаты для режима `payment`. */
  readonly payment: PaymentDraft;
  /** Касса выплаты; пустая строка — первая активная касса. */
  readonly cashId: string;
  readonly amount: string;
  /** Направление корректировки: зачисление или списание. */
  readonly direction: "credit" | "debit";
  readonly comment: string;
  readonly date: string;
}

/**
 * Форма команды клиента в режиме [mode]: оплата продажи или пополнение аванса, выплата аванса
 * или корректировка. Ошибка сервера оставляет форму открытой, успех закрывает панель.
 */
function ClientPaymentForm({
  api,
  branchId,
  clientId,
  summary,
  mode,
  saleId,
  onSaved,
}: Parameters<typeof ClientPaymentSheet>[0]) {
  const { t, format } = useI18n();
  const { close } = useEditSheet();
  const queries = useQueryClient();
  const accounts = useQuery({
    ...apiQuery(api, branchId, "finance/cash-accounts"),
    enabled: mode !== "adjust",
  });
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  const currency = summary.balance.currency;
  const today = todayLocalDate(useSession(api).orgInfo.timezone);
  const [failure, setFailure] = useState<string | null>(null);
  const zero = { currency, minorUnits: 0 };
  const saleDebt = (destination: string) =>
    summary.salesWithDebt.find((s) => s.saleId === destination);

  /** Запрос по значениям [value]; `null` — значения не прошли проверку. */
  const send = async (value: ClientPaymentValues): Promise<ApiResult<unknown> | null> => {
    const occurredOn = LocalDateSchema.safeParse(value.date);
    const comment = value.comment.trim() === "" ? null : value.comment.trim();
    if (mode === "payment") {
      const debt = saleDebt(value.destination);
      const payment = paymentInput(
        value.payment,
        available,
        debt?.debt ?? zero,
        debt === undefined ? zero : summary.advance,
      );
      if (payment === null || !occurredOn.success) {
        return null;
      }
      return debt === undefined
        ? api.call("finance/advance/top-up", {
            clientId,
            occurredOn: occurredOn.data,
            payment,
            comment,
          })
        : api.call("sales/pay", {
            saleId: debt.saleId,
            occurredOn: occurredOn.data,
            payment,
            comment,
          });
    }
    const amount = parseMoney(value.amount, currency);
    if (amount === null) {
      return null;
    }
    if (mode === "adjust") {
      return api.call("finance/advance/adjust", {
        clientId,
        amount: {
          ...amount,
          minorUnits: value.direction === "debit" ? -amount.minorUnits : amount.minorUnits,
        },
        comment: value.comment.trim(),
      });
    }
    const cashAccountId = CashAccountIdSchema.safeParse(
      value.cashId === "" ? available[0]?.id : value.cashId,
    );
    if (!cashAccountId.success || !occurredOn.success) {
      return null;
    }
    return api.call("finance/advance/payout", {
      clientId,
      cashAccountId: cashAccountId.data,
      amount,
      occurredOn: occurredOn.data,
      comment,
    });
  };

  const defaultValues: ClientPaymentValues = {
    destination: saleId ?? summary.salesWithDebt[0]?.saleId ?? "advance",
    payment: initialPayment(),
    cashId: "",
    amount: mode === "payout" ? moneyEditText(summary.advance) : "",
    direction: "credit",
    comment: "",
    date: today,
  };
  const form = useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      const result = await send(value);
      if (result === null) {
        setFailure(t("error.required"));
        return;
      }
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await queries.invalidateQueries({ queryKey: ["api", branchId] });
      await onSaved();
      close();
    },
  });
  return (
    <form.Subscribe
      selector={(state) => ({
        dirty: state.isDirty,
        submitting: state.isSubmitting,
        attempted: state.submissionAttempts > 0,
        destination: state.values.destination,
      })}
    >
      {({ dirty, submitting, attempted, destination }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          {mode === "payment" && (
            <>
              <form.Field name="destination">
                {(field) => (
                  <SelectField
                    field={field}
                    label={t("finance.destination")}
                    disabled={saleId !== undefined}
                    options={[
                      { value: "advance", label: t("finance.toAdvance") },
                      ...summary.salesWithDebt.map((s) => ({
                        value: s.saleId,
                        label: `${format.date(s.soldOn)} · ${format.money(s.debt)}`,
                      })),
                    ]}
                  />
                )}
              </form.Field>
              <form.Field name="payment">
                {(field) => (
                  <PaymentBlock
                    value={field.state.value}
                    onChange={field.handleChange}
                    accounts={available}
                    debt={saleDebt(destination)?.debt ?? zero}
                    advance={saleDebt(destination) === undefined ? zero : summary.advance}
                    canAccept
                    invalid={attempted}
                  />
                )}
              </form.Field>
            </>
          )}
          {mode === "adjust" && (
            <form.Field name="direction">
              {(field) => (
                <SelectField
                  field={field}
                  label={t("clients.detail.balanceDirection")}
                  options={[
                    { value: "credit", label: t("clients.detail.balanceCredit") },
                    { value: "debit", label: t("clients.detail.balanceDebit") },
                  ]}
                />
              )}
            </form.Field>
          )}
          {mode === "payout" && (
            <form.Field name="cashId">
              {(field) => (
                <SelectField
                  field={field}
                  label={t("finance.cashAccount")}
                  options={available.map((a) => ({ value: a.id, label: a.name }))}
                />
              )}
            </form.Field>
          )}
          {mode !== "payment" && (
            <form.Field
              name="amount"
              validators={{
                onSubmit: ({ value }) => {
                  const minorUnits = parseMoney(value, currency)?.minorUnits ?? 0;
                  return minorUnits > 0 &&
                    (mode !== "payout" || minorUnits <= summary.advance.minorUnits)
                    ? undefined
                    : t("error.invalidAmount");
                },
              }}
            >
              {(field) => (
                <TextField
                  field={field}
                  label={t("finance.amount")}
                  inputMode="decimal"
                  autoComplete="off"
                />
              )}
            </form.Field>
          )}
          {mode !== "adjust" && (
            <form.Field name="date">
              {(field) => (
                <TextField field={field} label={t("finance.date")} type="date" max={today} />
              )}
            </form.Field>
          )}
          <form.Field
            name="comment"
            validators={mode === "adjust" ? { onSubmit: requiredText(t) } : {}}
          >
            {(field) => <TextField field={field} label={t("finance.comment")} autoComplete="off" />}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
