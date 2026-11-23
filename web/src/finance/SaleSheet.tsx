import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  CashAccountIdSchema,
  LocalDateSchema,
  SaleIdSchema,
  type BranchId,
  type ClientDetailResponse,
  type SaleDetailResponse,
  type SaleId,
  type SaleOperationSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { CancellationForm } from "./CancellationForm";
import { ClientPaymentSheet } from "./ClientPaymentSheet";
import { FinanceForm } from "./FinanceForm";
import { PaymentBlock } from "./PaymentBlock";
import { initialPayment, paymentInput } from "./payment";

/** Продажа услуги с позициями и необязательной оплатой. */
export function SaleCreateSheet(props: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={props.open}
      onOpenChange={props.onOpenChange}
      title={t("finance.sellService")}
      size="lg"
    >
      <SaleCreateForm {...props} />
    </EditSheet>
  );
}

function SaleCreateForm({ api, branchId, client, onSaved }: Parameters<typeof SaleCreateSheet>[0]) {
  const { t, format } = useI18n();
  const session = useSession(api);
  const canAccept = hasPermission(session, "CAN_ACCEPT_PAYMENTS");
  const accounts = useQuery({
    ...apiQuery(api, branchId, "finance/cash-accounts"),
    enabled: canAccept,
  });
  const options = useQuery({
    ...apiQuery(api, branchId, "finance/client-payment-options", { clientId: client.id }),
    enabled: canAccept,
  });
  const [items, setItems] = useState(() => [{ key: uuidv7(), name: "", quantity: "1", price: "" }]);
  const [draft, setDraft] = useState(initialPayment);
  const [payNow, setPayNow] = useState(true);
  const today = todayLocalDate(session.orgInfo.timezone);
  const [date, setDate] = useState<string>(today);
  const [invalid, setInvalid] = useState(false);
  const queries = useQueryClient();
  const total = {
    ...client.balance,
    minorUnits: items.reduce(
      (sum, item) =>
        sum +
        (parseMoney(item.price, client.balance.currency)?.minorUnits ?? 0) * Number(item.quantity),
      0,
    ),
  };
  const advance = options.data?.advance ?? { ...client.balance, minorUnits: 0 };
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  return (
    <FinanceForm
      dirty={
        items.some((i) => i.name !== "" || i.price !== "") ||
        !payNow ||
        draft.fromAdvance !== "" ||
        draft.cash.some((r) => r.amount !== null || r.cashAccountId !== "")
      }
      submit={async () => {
        setInvalid(true);
        const parsed = items.map((item) => ({
          name: item.name.trim(),
          quantity: Number(item.quantity),
          unitPrice: parseMoney(item.price, client.balance.currency),
        }));
        if (
          !Number.isSafeInteger(total.minorUnits) ||
          parsed.length === 0 ||
          parsed.some(
            (item) =>
              item.name === "" ||
              !Number.isSafeInteger(item.quantity) ||
              item.quantity <= 0 ||
              item.quantity > 2147483647 ||
              item.unitPrice === null,
          )
        ) {
          return null;
        }
        const saleItems = parsed.flatMap((item) =>
          item.unitPrice === null ? [] : [{ ...item, unitPrice: item.unitPrice }],
        );
        const payment =
          canAccept && payNow && total.minorUnits > 0
            ? paymentInput(draft, available, total, advance)
            : null;
        if (canAccept && payNow && total.minorUnits > 0 && payment === null) {
          return null;
        }
        const soldOn = LocalDateSchema.safeParse(date);
        if (!soldOn.success) {
          return null;
        }
        return api.call("sales/create", {
          id: SaleIdSchema.parse(uuidv7()),
          clientId: client.id,
          soldOn: soldOn.data,
          items: saleItems,
          payment,
        });
      }}
      onSaved={async () => {
        await queries.invalidateQueries({ queryKey: ["api", branchId] });
        await onSaved();
      }}
    >
      <label className="block space-y-1">
        <span>{t("finance.date")}</span>
        <Input
          type="date"
          value={date}
          max={today}
          onChange={(e) => {
            setDate(e.target.value);
          }}
        />
      </label>
      {items.map((item) => (
        <fieldset key={item.key} className="space-y-2 rounded-lg border p-3">
          <label className="block space-y-1">
            <span>{t("finance.name")}</span>
            <Input
              value={item.name}
              aria-invalid={invalid && item.name.trim() === ""}
              onChange={(e) => {
                setItems(
                  items.map((i) => (i.key === item.key ? { ...i, name: e.target.value } : i)),
                );
              }}
            />
            {invalid && item.name.trim() === "" && (
              <span className="text-sm text-destructive">{t("error.required")}</span>
            )}
          </label>
          <div className="grid grid-cols-2 gap-2">
            <label>
              <span>{t("finance.quantity")}</span>
              <Input
                type="number"
                min="1"
                step="1"
                value={item.quantity}
                onChange={(e) => {
                  setItems(
                    items.map((i) => (i.key === item.key ? { ...i, quantity: e.target.value } : i)),
                  );
                }}
              />
            </label>
            <label>
              <span>{t("finance.price")}</span>
              <Input
                value={item.price}
                inputMode="decimal"
                aria-invalid={invalid && parseMoney(item.price, client.balance.currency) === null}
                onChange={(e) => {
                  setItems(
                    items.map((i) => (i.key === item.key ? { ...i, price: e.target.value } : i)),
                  );
                }}
              />
            </label>
          </div>
          {items.length > 1 && (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setItems(items.filter((i) => i.key !== item.key));
              }}
            >
              {t("finance.removeItem")}
            </Button>
          )}
        </fieldset>
      ))}
      <Button
        type="button"
        variant="outline"
        onClick={() => {
          setItems([...items, { key: uuidv7(), name: "", quantity: "1", price: "" }]);
        }}
      >
        {t("finance.addItem")}
      </Button>
      <p className="font-medium">
        {t("finance.total")}: {format.money(total)}
      </p>
      {canAccept && (
        <>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={payNow}
              onChange={(e) => {
                setPayNow(e.target.checked);
              }}
            />
            {t("finance.payNow")}
          </label>
          {payNow && (
            <PaymentBlock
              value={draft}
              onChange={setDraft}
              accounts={available}
              debt={total}
              advance={advance}
              canAccept
              invalid={invalid}
            />
          )}
        </>
      )}
    </FinanceForm>
  );
}

/** Общая панель продажи из истории баланса, абонемента и кассового журнала. */
export function SaleSheet(props: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly saleId: SaleId | null;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={props.open && props.saleId !== null}
      onOpenChange={props.onOpenChange}
      title={t("finance.sale")}
      size="lg"
    >
      {props.saleId !== null && (
        <SalePanel api={props.api} branchId={props.branchId} saleId={props.saleId} />
      )}
    </EditSheet>
  );
}

function SalePanel({
  api,
  branchId,
  saleId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly saleId: SaleId;
}) {
  const { t } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "sales/detail", { saleId }));
  if (query.data === undefined) {
    return (
      <EditSheetBody>
        {query.isError ? (
          <FormAlert message={t("finance.loadError")} />
        ) : (
          <Skeleton className="h-40" />
        )}
      </EditSheetBody>
    );
  }
  return <LoadedSalePanel api={api} branchId={branchId} sale={query.data} />;
}

function LoadedSalePanel({
  api,
  branchId,
  sale,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly sale: SaleDetailResponse;
}) {
  const { t, format } = useI18n();
  const session = useSession(api);
  const canAccept = hasPermission(session, "CAN_ACCEPT_PAYMENTS");
  const canManage = hasPermission(session, "CAN_MANAGE_FINANCE");
  const queries = useQueryClient();
  const [action, setAction] = useState<"payment" | "return" | "cancel" | null>(null);
  const [reversing, setReversing] = useState<SaleOperationSchema | null>(null);
  const options = useQuery({
    ...apiQuery(api, branchId, "finance/client-payment-options", { clientId: sale.clientId }),
    enabled: canAccept,
  });
  const refresh = async () => {
    await queries.invalidateQueries({ queryKey: ["api", branchId] });
  };
  return (
    <EditSheetBody>
      <p>
        {format.date(sale.soldOn)} · {sale.performedBy?.name}
      </p>
      {sale.cancelledAt !== null && (
        <p className="rounded-lg border p-3">
          {t("finance.cancelled")} · {sale.cancelReason}
        </p>
      )}
      <p className="font-medium">{t(`finance.state.${sale.paymentState}`)}</p>
      <ul className="divide-y rounded-lg border">
        {sale.items.map((item) => (
          <li key={item.id} className="p-3">
            <p>
              {item.name} · {item.quantity} × {format.money(item.unitPrice)}
            </p>
            <p className="font-medium">{format.money(item.amount)}</p>
          </li>
        ))}
      </ul>
      <dl className="grid grid-cols-2 gap-2">
        <dt>{t("finance.total")}</dt>
        <dd>{format.money(sale.total)}</dd>
        <dt>{t("finance.paid")}</dt>
        <dd>{format.money(sale.paid)}</dd>
        <dt>{t("finance.returned")}</dt>
        <dd>{format.money(sale.returned)}</dd>
        <dt>{t("finance.toPay")}</dt>
        <dd>{format.money(sale.debt)}</dd>
      </dl>
      {sale.cancelledAt === null && (
        <div className="flex flex-wrap gap-2">
          {canAccept && sale.debt.minorUnits > 0 && (
            <Button
              type="button"
              onClick={() => {
                setAction("payment");
              }}
            >
              {t("finance.payment")}
            </Button>
          )}
          {canManage && (
            <>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setAction("return");
                }}
              >
                {t("finance.return")}
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={() => {
                  setAction("cancel");
                }}
              >
                {t("finance.cancelSale")}
              </Button>
            </>
          )}
        </div>
      )}
      <ul className="divide-y rounded-lg border">
        {sale.operations.map((operation) => (
          <li key={operation.id} className="space-y-1 p-3">
            <p>
              {t(`finance.kind.${operation.kind}`)} · {format.money(operation.amount)} ·{" "}
              {format.date(operation.occurredOn)}
            </p>
            <p className="text-sm text-muted-foreground">
              {operation.performedBy?.name} · {operation.comment}
            </p>
            {operation.cash.map((part, index) => (
              <p key={index}>
                {part.cashAccountName}: {format.money(part.amount)}
              </p>
            ))}
            {operation.fromAdvance.minorUnits > 0 && (
              <p>
                {t("finance.fromAdvance")}: {format.money(operation.fromAdvance)}
              </p>
            )}
            {operation.reversed ? (
              <p>{t("finance.cancelled")}</p>
            ) : (
              sale.cancelledAt === null &&
              operation.canReverse && (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setReversing(operation);
                  }}
                >
                  {t("finance.cancelOperation")}
                </Button>
              )
            )}
          </li>
        ))}
      </ul>
      {options.data !== undefined && (
        <ClientPaymentSheet
          api={api}
          branchId={branchId}
          clientId={sale.clientId}
          summary={options.data}
          mode="payment"
          saleId={sale.id}
          open={action === "payment"}
          onOpenChange={(open) => {
            if (!open) {
              setAction(null);
            }
          }}
          onSaved={refresh}
        />
      )}
      <EditSheet
        open={action === "return"}
        onOpenChange={(open) => {
          if (!open) {
            setAction(null);
          }
        }}
        title={t("finance.return")}
      >
        <SaleReturnForm api={api} branchId={branchId} sale={sale} onSaved={refresh} />
      </EditSheet>
      <EditSheet
        open={action === "cancel"}
        onOpenChange={(open) => {
          if (!open) {
            setAction(null);
          }
        }}
        title={t("finance.cancelSale")}
      >
        <CancellationForm api={api} operation={{ kind: "sale", id: sale.id }} onSaved={refresh} />
      </EditSheet>
      <EditSheet
        open={reversing !== null}
        onOpenChange={(open) => {
          if (!open) {
            setReversing(null);
          }
        }}
        title={t("finance.cancelOperation")}
      >
        {reversing !== null && (
          <CancellationForm
            api={api}
            operation={{ kind: "payment", id: reversing.id }}
            onSaved={refresh}
          />
        )}
      </EditSheet>
    </EditSheetBody>
  );
}

function SaleReturnForm({
  api,
  branchId,
  sale,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly sale: SaleDetailResponse;
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const accounts = useQuery(apiQuery(api, branchId, "finance/cash-accounts"));
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [payOut, setPayOut] = useState(false);
  const [cashId, setCashId] = useState("");
  const [invalid, setInvalid] = useState(false);
  return (
    <FinanceForm
      dirty={amount !== "" || reason !== "" || payOut}
      onSaved={onSaved}
      submit={async () => {
        setInvalid(true);
        const parsed = parseMoney(amount, sale.total.currency);
        const cash = CashAccountIdSchema.safeParse(cashId === "" ? available[0]?.id : cashId);
        if (
          parsed === null ||
          parsed.minorUnits <= 0 ||
          parsed.minorUnits > sale.total.minorUnits - sale.returned.minorUnits ||
          reason.trim() === "" ||
          (payOut && !cash.success)
        ) {
          return null;
        }
        return api.call("sales/return", {
          saleId: sale.id,
          amount: parsed,
          reason: reason.trim(),
          cashAccountId: payOut && cash.success ? cash.data : null,
        });
      }}
    >
      <label className="block space-y-1">
        <span>{t("finance.amount")}</span>
        <Input
          inputMode="decimal"
          value={amount}
          aria-invalid={invalid && (parseMoney(amount, sale.total.currency)?.minorUnits ?? 0) <= 0}
          onChange={(e) => {
            setAmount(e.target.value);
          }}
        />
      </label>
      <label className="block space-y-1">
        <span>{t("finance.reason")}</span>
        <Input
          value={reason}
          aria-invalid={invalid && reason.trim() === ""}
          onChange={(e) => {
            setReason(e.target.value);
          }}
        />
      </label>
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={payOut}
          onChange={(e) => {
            setPayOut(e.target.checked);
          }}
        />
        {t("finance.payOutReturn")}
      </label>
      {payOut && (
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
    </FinanceForm>
  );
}
