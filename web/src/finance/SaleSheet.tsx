import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  CashAccountIdSchema,
  LocalDateSchema,
  SaleIdSchema,
  type BranchId,
  type ClientDetailResponse,
  type Currency,
  type Money,
  type SaleDetailResponse,
  type SaleId,
  type SaleOperationSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { manualField, type BoundField } from "@/forms/field";
import { CheckboxField, SelectField, TextField } from "@/forms/fields";
import { requiredText } from "@/forms/requiredText";
import { useI18n, type I18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { CancellationForm } from "./CancellationForm";
import { ClientPaymentSheet } from "./ClientPaymentSheet";
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

/** Позиция продажи в форме: ключ строки и поля, вводимые текстом. */
interface SaleItemDraft {
  readonly key: string;
  readonly name: string;
  readonly quantity: string;
  readonly price: string;
}

/** Новая позиция продажи: одна штука без названия и цены. */
function emptyItem(): SaleItemDraft {
  return { key: uuidv7(), name: "", quantity: "1", price: "" };
}

/** Количество из текста [text]: положительное целое в пределах `Int` Kotlin, иначе `null`. */
function quantityOf(text: string): number | null {
  const value = Number(text);
  return Number.isSafeInteger(value) && value > 0 && value <= 2147483647 ? value : null;
}

/** Ошибки полей позиции продажи; `null` — поле заполнено верно. */
interface SaleItemErrors {
  readonly name: string | null;
  readonly quantity: string | null;
  readonly price: string | null;
}

/** Позиция без ошибок: до первой попытки отправки поля не подсвечиваются. */
const noItemErrors: SaleItemErrors = { name: null, quantity: null, price: null };

/** Ошибки полей позиции [item] с ценой в валюте [currency] и сообщениями на языке [t]. */
function itemErrors(item: SaleItemDraft, currency: Currency, t: I18n["t"]): SaleItemErrors {
  return {
    name: item.name.trim() === "" ? t("error.required") : null,
    quantity: quantityOf(item.quantity) === null ? t("error.positiveInteger") : null,
    price: parseMoney(item.price, currency) === null ? t("error.invalidAmount") : null,
  };
}

/**
 * Поле позиции продажи для обёрток из `@/forms/fields`: значение [value] с ошибкой [error].
 * Позиции — одно поле-массив формы, поэтому их поля связаны вручную, а не через `form.Field`.
 */
function itemField(
  name: string,
  value: string,
  error: string | null,
  onChange: (value: string) => void,
): BoundField<string> {
  return {
    ...manualField(name, value, onChange),
    state: { value, meta: { errors: error === null ? [] : [error] } },
  };
}

/** Стоимость позиций [items] в валюте [currency]; нераспознанная цена считается нулём. */
function saleTotal(items: readonly SaleItemDraft[], currency: Currency): Money {
  return {
    currency,
    minorUnits: items.reduce(
      (sum, item) =>
        sum + (parseMoney(item.price, currency)?.minorUnits ?? 0) * Number(item.quantity),
      0,
    ),
  };
}

/**
 * Форма продажи клиенту [client]: дата, позиции и, при праве приёма оплат, оплата сразу.
 * Ошибка сервера оставляет форму открытой, успех вызывает [onSaved] и закрывает панель.
 */
function SaleCreateForm({ api, branchId, client, onSaved }: Parameters<typeof SaleCreateSheet>[0]) {
  const { t, format } = useI18n();
  const { close } = useEditSheet();
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
  const currency = client.balance.currency;
  const today: string = todayLocalDate(session.orgInfo.timezone);
  const queries = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);
  const advance = options.data?.advance ?? { currency, minorUnits: 0 };
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  const form = useForm({
    defaultValues: {
      date: today,
      items: [emptyItem()],
      payNow: true,
      payment: initialPayment(),
    },
    onSubmit: async ({ value }) => {
      const total = saleTotal(value.items, currency);
      const soldOn = LocalDateSchema.safeParse(value.date);
      const items = value.items.flatMap((item) => {
        const quantity = quantityOf(item.quantity);
        const unitPrice = parseMoney(item.price, currency);
        return quantity === null || unitPrice === null
          ? []
          : [{ name: item.name.trim(), quantity, unitPrice }];
      });
      if (!soldOn.success) {
        setFailure(t("error.required"));
        return;
      }
      if (items.length !== value.items.length || items.some((item) => item.name === "")) {
        return;
      }
      if (!Number.isSafeInteger(total.minorUnits)) {
        setFailure(t("error.invalidAmount"));
        return;
      }
      const paid = canAccept && value.payNow && total.minorUnits > 0;
      const payment = paid ? paymentInput(value.payment, available, total, advance) : null;
      setFailure(null);
      if (paid && payment === null) {
        return;
      }
      const result = await api.call("sales/create", {
        id: SaleIdSchema.parse(uuidv7()),
        clientId: client.id,
        soldOn: soldOn.data,
        items,
        payment,
      });
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
        total: saleTotal(state.values.items, currency),
        payNow: state.values.payNow,
      })}
    >
      {({ dirty, submitting, attempted, total, payNow }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="date">
            {(field) => (
              <TextField field={field} label={t("finance.date")} type="date" max={today} />
            )}
          </form.Field>
          <form.Field name="items">
            {(itemsField) => {
              const items = itemsField.state.value;
              const edit = (key: string, change: Partial<SaleItemDraft>) => {
                itemsField.handleChange(
                  items.map((i) => (i.key === key ? { ...i, ...change } : i)),
                );
              };
              return (
                <>
                  {items.map((item) => {
                    const errors = attempted ? itemErrors(item, currency, t) : noItemErrors;
                    return (
                      <fieldset key={item.key} className="space-y-2 rounded-lg border p-3">
                        <TextField
                          field={itemField("name", item.name, errors.name, (name) => {
                            edit(item.key, { name });
                          })}
                          label={t("finance.name")}
                          autoComplete="off"
                        />
                        <div className="grid grid-cols-2 gap-2">
                          <TextField
                            field={itemField(
                              "quantity",
                              item.quantity,
                              errors.quantity,
                              (quantity) => {
                                edit(item.key, { quantity });
                              },
                            )}
                            label={t("finance.quantity")}
                            inputMode="numeric"
                            autoComplete="off"
                          />
                          <TextField
                            field={itemField("price", item.price, errors.price, (price) => {
                              edit(item.key, { price });
                            })}
                            label={t("finance.price")}
                            inputMode="decimal"
                            autoComplete="off"
                          />
                        </div>
                        {items.length > 1 && (
                          <Button
                            type="button"
                            variant="ghost"
                            onClick={() => {
                              itemsField.handleChange(items.filter((i) => i.key !== item.key));
                            }}
                          >
                            {t("finance.removeItem")}
                          </Button>
                        )}
                      </fieldset>
                    );
                  })}
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      itemsField.handleChange([...items, emptyItem()]);
                    }}
                  >
                    {t("finance.addItem")}
                  </Button>
                </>
              );
            }}
          </form.Field>
          <p className="font-medium">
            {t("finance.total")}: {format.money(total)}
          </p>
          {canAccept && (
            <>
              <form.Field name="payNow">
                {(field) => <CheckboxField field={field} label={t("finance.payNow")} />}
              </form.Field>
              {payNow && (
                <form.Field name="payment">
                  {(field) => (
                    <PaymentBlock
                      value={field.state.value}
                      onChange={field.handleChange}
                      accounts={available}
                      debt={total}
                      advance={advance}
                      canAccept
                      invalid={attempted}
                    />
                  )}
                </form.Field>
              )}
            </>
          )}
        </EditSheetForm>
      )}
    </form.Subscribe>
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

/**
 * Форма возврата по продаже [sale]: сумма не больше невозвращённой, обязательная причина
 * и, при выплате, касса — по умолчанию первая активная.
 */
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
  const { close } = useEditSheet();
  const accounts = useQuery(apiQuery(api, branchId, "finance/cash-accounts"));
  const available = (accounts.data?.accounts ?? []).filter((a) => !a.archived);
  const [failure, setFailure] = useState<string | null>(null);
  const returnable = sale.total.minorUnits - sale.returned.minorUnits;
  const form = useForm({
    defaultValues: { amount: "", reason: "", payOut: false, cashId: "" },
    onSubmit: async ({ value }) => {
      const amount = parseMoney(value.amount, sale.total.currency);
      const cash = CashAccountIdSchema.safeParse(
        value.cashId === "" ? available[0]?.id : value.cashId,
      );
      if (amount === null || (value.payOut && !cash.success)) {
        setFailure(t("error.required"));
        return;
      }
      setFailure(null);
      const result = await api.call("sales/return", {
        saleId: sale.id,
        amount,
        reason: value.reason.trim(),
        cashAccountId: value.payOut && cash.success ? cash.data : null,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await onSaved();
      close();
    },
  });
  return (
    <form.Subscribe
      selector={(state) => ({
        dirty: state.isDirty,
        submitting: state.isSubmitting,
        payOut: state.values.payOut,
      })}
    >
      {({ dirty, submitting, payOut }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field
            name="amount"
            validators={{
              onSubmit: ({ value }) => {
                const minorUnits = parseMoney(value, sale.total.currency)?.minorUnits ?? 0;
                return minorUnits > 0 && minorUnits <= returnable
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
          <form.Field name="reason" validators={{ onSubmit: requiredText(t) }}>
            {(field) => <TextField field={field} label={t("finance.reason")} autoComplete="off" />}
          </form.Field>
          <form.Field name="payOut">
            {(field) => <CheckboxField field={field} label={t("finance.payOutReturn")} />}
          </form.Field>
          {payOut && (
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
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
