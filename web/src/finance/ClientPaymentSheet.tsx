import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  CashAccountIdSchema,
  LocalDateSchema,
  type BranchId,
  type ClientId,
  type ClientLedgerResponse,
  type SaleId,
} from "@/api/generated/contracts";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { moneyEditText, parseMoney } from "@/lib/money";
import { apiQuery } from "@/query/queries";
import { EditSheet } from "@/ui/EditSheet";
import { FinanceForm } from "./FinanceForm";
import { PaymentBlock } from "./PaymentBlock";
import { initialPayment, paymentInput } from "./payment";

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
  const queries = useQueryClient();
  const accounts = useQuery({
    ...apiQuery(api, branchId, "finance/cash-accounts"),
    enabled: mode !== "adjust",
  });
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  const [destination, setDestination] = useState<string>(
    saleId ?? summary.salesWithDebt[0]?.saleId ?? "advance",
  );
  const [draft, setDraft] = useState(initialPayment);
  const [cashId, setCashId] = useState("");
  const [amount, setAmount] = useState(mode === "payout" ? moneyEditText(summary.advance) : "");
  const [direction, setDirection] = useState("credit");
  const [comment, setComment] = useState("");
  const [date, setDate] = useState<string>(todayLocalDate());
  const [invalid, setInvalid] = useState(false);
  const debt = summary.salesWithDebt.find((s) => s.saleId === destination);
  const due = debt?.debt ?? { ...summary.balance, minorUnits: 0 };
  const dirty =
    amount !== (mode === "payout" ? moneyEditText(summary.advance) : "") ||
    direction !== "credit" ||
    comment !== "" ||
    date !== todayLocalDate() ||
    cashId !== "" ||
    destination !== (saleId ?? summary.salesWithDebt[0]?.saleId ?? "advance") ||
    draft.fromAdvance !== "" ||
    draft.cash.length !== 1 ||
    draft.cash.some((row) => row.amount !== null || row.cashAccountId !== "");
  return (
    <FinanceForm
      dirty={dirty}
      submit={async () => {
        setInvalid(true);
        const occurredOn = LocalDateSchema.safeParse(date);
        if (mode === "payment") {
          const payment = paymentInput(
            draft,
            available,
            due,
            debt === undefined ? { ...summary.advance, minorUnits: 0 } : summary.advance,
          );
          if (payment === null || !occurredOn.success) return null;
          return debt === undefined
            ? api.call("finance/advance/top-up", {
                clientId,
                occurredOn: occurredOn.data,
                payment,
                comment: comment.trim() === "" ? null : comment.trim(),
              })
            : api.call("sales/pay", {
                saleId: debt.saleId,
                occurredOn: occurredOn.data,
                payment,
                comment: comment.trim() === "" ? null : comment.trim(),
              });
        }
        const parsed = parseMoney(amount, summary.balance.currency);
        if (parsed === null || parsed.minorUnits <= 0) return null;
        if (mode === "adjust") {
          if (comment.trim() === "") return null;
          return api.call("finance/advance/adjust", {
            clientId,
            amount: {
              ...parsed,
              minorUnits: direction === "debit" ? -parsed.minorUnits : parsed.minorUnits,
            },
            comment: comment.trim(),
          });
        }
        const cashAccountId = CashAccountIdSchema.safeParse(
          cashId === "" ? available[0]?.id : cashId,
        );
        if (
          !cashAccountId.success ||
          !occurredOn.success ||
          parsed.minorUnits > summary.advance.minorUnits
        )
          return null;
        return api.call("finance/advance/payout", {
          clientId,
          cashAccountId: cashAccountId.data,
          amount: parsed,
          occurredOn: occurredOn.data,
          comment: comment.trim() === "" ? null : comment.trim(),
        });
      }}
      onSaved={async () => {
        await queries.invalidateQueries({ queryKey: ["api", branchId] });
        await onSaved();
      }}
    >
      {mode === "payment" && (
        <>
          <label className="block space-y-1">
            <span>{t("finance.destination")}</span>
            <NativeSelect
              value={destination}
              disabled={saleId !== undefined}
              onChange={(e) => {
                setDestination(e.target.value);
              }}
            >
              <option value="advance">{t("finance.toAdvance")}</option>
              {summary.salesWithDebt.map((s) => (
                <option key={s.saleId} value={s.saleId}>
                  {format.date(s.soldOn)} · {format.money(s.debt)}
                </option>
              ))}
            </NativeSelect>
          </label>
          <PaymentBlock
            value={draft}
            onChange={setDraft}
            accounts={available}
            debt={due}
            advance={debt === undefined ? { ...summary.advance, minorUnits: 0 } : summary.advance}
            canAccept
            invalid={invalid}
          />
        </>
      )}
      {mode === "adjust" && (
        <label className="block space-y-1">
          <span>{t("clients.detail.balanceDirection")}</span>
          <NativeSelect
            value={direction}
            onChange={(e) => {
              setDirection(e.target.value);
            }}
          >
            <option value="credit">{t("clients.detail.balanceCredit")}</option>
            <option value="debit">{t("clients.detail.balanceDebit")}</option>
          </NativeSelect>
        </label>
      )}
      {mode === "payout" && (
        <label className="block space-y-1">
          <span>{t("finance.cashAccount")}</span>
          <NativeSelect
            value={cashId === "" ? (available[0]?.id ?? "") : cashId}
            onChange={(e) => {
              setCashId(e.target.value);
            }}
          >
            <option value="">{t("finance.select")}</option>
            {available.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </NativeSelect>
        </label>
      )}
      {mode !== "payment" && (
        <label className="block space-y-1">
          <span>{t("finance.amount")}</span>
          <Input
            value={amount}
            inputMode="decimal"
            aria-invalid={
              invalid && (parseMoney(amount, summary.balance.currency)?.minorUnits ?? 0) <= 0
            }
            onChange={(e) => {
              setAmount(e.target.value);
            }}
          />
          {invalid && (parseMoney(amount, summary.balance.currency)?.minorUnits ?? 0) <= 0 && (
            <span className="text-sm text-destructive">{t("error.invalidAmount")}</span>
          )}
        </label>
      )}
      {mode !== "adjust" && (
        <label className="block space-y-1">
          <span>{t("finance.date")}</span>
          <Input
            type="date"
            value={date}
            max={todayLocalDate()}
            onChange={(e) => {
              setDate(e.target.value);
            }}
          />
        </label>
      )}
      <label className="block space-y-1">
        <span>{t("finance.comment")}</span>
        <Input
          value={comment}
          aria-invalid={invalid && mode === "adjust" && comment.trim() === ""}
          onChange={(e) => {
            setComment(e.target.value);
          }}
        />
        {invalid && mode === "adjust" && comment.trim() === "" && (
          <span className="text-sm text-destructive">{t("error.required")}</span>
        )}
      </label>
    </FinanceForm>
  );
}
