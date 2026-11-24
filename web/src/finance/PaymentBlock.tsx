import { useId } from "react";
import { CashAccountIdSchema, type CashAccountSchema, type Money } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";
import { moneyEditText, parseMoney } from "@/lib/money";
import { paymentInput, type PaymentDraft } from "./payment";
import { uuidv7 } from "@/lib/uuid";

/** Общий блок оплаты для продажи, выдачи абонемента и пополнения аванса. */
export function PaymentBlock({
  value,
  onChange,
  accounts,
  debt,
  advance,
  canAccept,
  invalid = false,
}: {
  readonly value: PaymentDraft;
  readonly onChange: (value: PaymentDraft) => void;
  readonly accounts: readonly CashAccountSchema[];
  readonly debt: Money;
  readonly advance: Money;
  readonly canAccept: boolean;
  readonly invalid?: boolean;
}) {
  const { t, format } = useI18n();
  const id = useId();
  if (!canAccept) {
    return null;
  }
  const fromAdvance =
    parseMoney(value.fromAdvance === "" ? "0" : value.fromAdvance, debt.currency)?.minorUnits ?? 0;
  const automatic = { ...debt, minorUnits: Math.max(0, debt.minorUnits - fromAdvance) };
  const total = value.cash.reduce(
    (sum, row) =>
      sum +
      (row.amount === null
        ? automatic.minorUnits
        : (parseMoney(row.amount, debt.currency)?.minorUnits ?? 0)),
    fromAdvance,
  );
  return (
    <fieldset className="min-w-0 space-y-3 rounded-lg border p-3">
      <legend className="px-1 font-medium">{t("finance.payment")}</legend>
      <p>
        {t("finance.toPay")}: {format.money(debt)}
      </p>
      {value.cash.map((row, index) => (
        <div
          key={row.key}
          className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] items-end gap-2"
        >
          <label className="min-w-0 space-y-1" htmlFor={`${id}-${row.key}-cash`}>
            <span>{t("finance.cashAccount")}</span>
            <NativeSelect
              id={`${id}-${row.key}-cash`}
              value={
                row.cashAccountId === ""
                  ? (accounts.find((a) => !a.archived)?.id ?? "")
                  : row.cashAccountId
              }
              onChange={(e) => {
                const parsed = CashAccountIdSchema.safeParse(e.target.value);
                onChange({
                  ...value,
                  cash: value.cash.map((r) =>
                    r.key === row.key
                      ? { ...r, cashAccountId: parsed.success ? parsed.data : "" }
                      : r,
                  ),
                });
              }}
            >
              <option value="">{t("finance.select")}</option>
              {accounts
                .filter((a) => !a.archived)
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
            </NativeSelect>
          </label>
          <label className="min-w-0 space-y-1" htmlFor={`${id}-${row.key}-amount`}>
            <span>{t("finance.amount")}</span>
            <Input
              id={`${id}-${row.key}-amount`}
              inputMode="decimal"
              value={row.amount ?? moneyEditText(automatic)}
              aria-invalid={invalid && paymentInput(value, accounts, debt, advance) === null}
              onChange={(e) => {
                onChange({
                  ...value,
                  cash: value.cash.map((r) =>
                    r.key === row.key ? { ...r, amount: e.target.value } : r,
                  ),
                });
              }}
            />
          </label>
          <Button
            type="button"
            variant="ghost"
            aria-label={t("finance.removePart")}
            disabled={index === 0}
            onClick={() => {
              onChange({ ...value, cash: value.cash.filter((r) => r.key !== row.key) });
            }}
          >
            ×
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        onClick={() => {
          onChange({
            ...value,
            cash: [
              ...value.cash,
              { key: uuidv7(), cashAccountId: accounts[0]?.id ?? "", amount: "0" },
            ],
          });
        }}
      >
        {t("finance.addPart")}
      </Button>
      {advance.minorUnits > 0 && (
        <label className="block space-y-1" htmlFor={`${id}-advance`}>
          <span>
            {t("finance.fromAdvance")} · {format.money(advance)}
          </span>
          <Input
            id={`${id}-advance`}
            value={value.fromAdvance}
            inputMode="decimal"
            aria-invalid={
              invalid && (fromAdvance > advance.minorUnits || fromAdvance > debt.minorUnits)
            }
            onChange={(e) => {
              onChange({ ...value, fromAdvance: e.target.value });
            }}
          />
        </label>
      )}
      <p aria-live="polite">
        {t("finance.total")}: {format.money({ ...debt, minorUnits: total })}
      </p>
      {total > debt.minorUnits && (
        <p role="status" className="text-sm text-emerald-700">
          {t("finance.overpayment", {
            amount: format.money({ ...debt, minorUnits: total - debt.minorUnits }),
          })}
        </p>
      )}
      {invalid && paymentInput(value, accounts, debt, advance) === null && (
        <p role="alert" className="text-sm text-destructive">
          {t("error.invalidAmount")}
        </p>
      )}
    </fieldset>
  );
}
