import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { FormAlert } from "@/forms/FormAlert";
import { RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { parseMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";

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

/**
 * Раздел баланса клиента в карточке: текущий баланс, корректировка и история операций.
 * Показывается, только если у сотрудника есть право `CAN_VIEW_CLIENT_BALANCE`
 * (проверка — в вызывающем компоненте, у которого есть сессия).
 */
export function ClientBalanceSection({
  api,
  branchId,
  clientId,
  balance,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly balance: Money;
  readonly onChanged: (client: ClientDetailResponse) => void;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  return (
    <div className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold">{t("clients.detail.balance")}</h2>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              setHistoryOpen(true);
            }}
          >
            {t("clients.detail.balanceHistory")}
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={() => {
              setAdjustOpen(true);
            }}
          >
            {t("clients.detail.balanceAdjust")}
          </Button>
        </div>
      </div>
      <p className={cn("text-2xl font-semibold", balance.minorUnits < 0 && "text-destructive")}>
        {format.money(balance)}
      </p>

      {adjustOpen && (
        <AdjustBalanceDialog
          api={api}
          clientId={clientId}
          currency={balance.currency}
          onSuccess={(client) => {
            setAdjustOpen(false);
            onChanged(client);
            void queryClient.invalidateQueries({
              queryKey: ["api", branchId, "clients/balance/history", { id: clientId }],
            });
          }}
          onClose={() => {
            setAdjustOpen(false);
          }}
        />
      )}

      <BalanceHistorySheet
        api={api}
        branchId={branchId}
        clientId={clientId}
        open={historyOpen}
        onOpenChange={setHistoryOpen}
      />
    </div>
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

/** Панель истории операций по балансу клиента, в обратном хронологическом порядке. */
function BalanceHistorySheet({
  api,
  branchId,
  clientId,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t, format } = useI18n();
  const historyQuery = apiQuery(api, branchId, "clients/balance/history", { id: clientId });
  const history = useQuery({ ...historyQuery, enabled: open });

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{t("clients.detail.balanceHistory")}</SheetTitle>
          <SheetDescription className="sr-only">
            {t("clients.detail.balanceHistory")}
          </SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-4">
          {history.isError && <FormAlert message={t("clients.detail.loadError")} />}
          {history.data !== undefined &&
            (history.data.entries.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {t("clients.detail.balanceHistoryEmpty")}
              </p>
            ) : (
              <ul className="divide-y">
                {history.data.entries.map((entry) => (
                  <li key={entry.id} className="space-y-1 py-3 first:pt-0">
                    <div className="flex items-baseline justify-between gap-2">
                      <span
                        className={cn(
                          "font-medium",
                          entry.amount.minorUnits < 0 ? "text-destructive" : "text-primary",
                        )}
                      >
                        {entry.amount.minorUnits > 0 ? "+" : ""}
                        {format.money(entry.amount)}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {format.money(entry.balanceAfter)}
                      </span>
                    </div>
                    {entry.note !== null && entry.note !== "" && (
                      <p className="text-sm">{entry.note}</p>
                    )}
                    <p className="text-xs text-muted-foreground">
                      {[entry.performedBy?.name, format.dateTime(entry.createdAt)]
                        .filter((part) => part !== undefined && part !== "")
                        .join(" · ")}
                    </p>
                  </li>
                ))}
              </ul>
            ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}
