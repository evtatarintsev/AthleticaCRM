import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientDetailResponse, SaleId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ClientPaymentSheet, type ClientPaymentMode } from "@/finance/ClientPaymentSheet";
import { SaleSheet } from "@/finance/SaleSheet";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";

/** Баланс, аванс, долги и полная история клиента; команды открываются вложенными панелями. */
export function ClientBalanceSheet(props: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly client: ClientDetailResponse;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly onChanged: (client: ClientDetailResponse) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={props.open}
      onOpenChange={props.onOpenChange}
      title={t("clientCard.balance")}
      size="lg"
    >
      <ClientBalancePanel {...props} />
    </EditSheet>
  );
}

function ClientBalancePanel({
  api,
  branchId,
  client,
  onChanged,
}: Parameters<typeof ClientBalanceSheet>[0]) {
  const { t, format } = useI18n();
  const session = useSession(api);
  const history = useQuery(
    apiQuery(api, branchId, "finance/client-ledger", { clientId: client.id }),
  );
  const [mode, setMode] = useState<ClientPaymentMode | null>(null);
  const [saleId, setSaleId] = useState<SaleId | null>(null);
  const zero = { ...client.balance, minorUnits: 0 };
  const summary = history.data ?? {
    balance: client.balance,
    advance: zero,
    debt: zero,
    entries: [],
    salesWithDebt: [],
  };
  const active = client.state === "ACTIVE";
  return (
    <EditSheetBody>
      <div className="space-y-3 rounded-lg border p-4">
        <p
          className={cn(
            "text-2xl font-semibold tabular-nums",
            summary.balance.minorUnits < 0 && "text-destructive",
          )}
        >
          {format.money(summary.balance)}
        </p>
        {history.data !== undefined && (
          <p className="text-sm">
            {t("finance.advance")}: {format.money(summary.advance)} · {t("finance.debt")}:{" "}
            {format.money(summary.debt)}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          {active &&
            hasPermission(session, "CAN_ACCEPT_PAYMENTS") &&
            history.data !== undefined && (
              <Button
                type="button"
                onClick={() => {
                  setMode("payment");
                }}
              >
                {t("finance.payment")}
              </Button>
            )}
          {active &&
            hasPermission(session, "CAN_MANAGE_FINANCE") &&
            summary.advance.minorUnits > 0 && (
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setMode("payout");
                }}
              >
                {t("finance.payout")}
              </Button>
            )}
          {active && hasPermission(session, "CAN_MANAGE_FINANCE") && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setMode("adjust");
              }}
            >
              {t("finance.adjust")}
            </Button>
          )}
        </div>
      </div>
      <h2 className="font-semibold">{t("clientCard.balanceHistory")}</h2>
      {history.isPending && <Skeleton className="h-24" />}
      {history.isError && <FormAlert message={t("clientCard.balanceHistoryLoadError")} />}
      {history.isSuccess && summary.entries.length === 0 && (
        <p className="py-8 text-center text-muted-foreground">
          {t("clients.detail.balanceHistoryEmpty")}
        </p>
      )}
      <ul className="divide-y rounded-lg border">
        {summary.entries.map((entry) => (
          <li key={entry.id} className="flex items-start justify-between gap-3 p-3">
            <div className="min-w-0 space-y-1">
              {entry.saleId === null ? (
                <p className="font-medium">{t(`finance.kind.${entry.kind}`)}</p>
              ) : (
                <Button
                  type="button"
                  variant="link"
                  className="h-auto p-0"
                  onClick={() => {
                    setSaleId(entry.saleId);
                  }}
                >
                  {t(`finance.kind.${entry.kind}`)}
                </Button>
              )}
              {entry.reversed && <p className="text-sm">{t("finance.cancelled")}</p>}
              {entry.comment !== null && <p className="break-words text-sm">{entry.comment}</p>}
              <p className="text-xs text-muted-foreground">
                {format.date(entry.occurredOn)} · {format.dateTime(entry.createdAt)} ·{" "}
                {entry.performedBy?.name}
              </p>
              {entry.fromAdvance.minorUnits > 0 && (
                <p className="text-xs">
                  {t("finance.fromAdvance")}: {format.money(entry.fromAdvance)}
                </p>
              )}
            </div>
            <div className="shrink-0 text-right">
              <p
                className={cn(
                  "tabular-nums",
                  entry.amount.minorUnits < 0 && "text-destructive",
                  entry.amount.minorUnits > 0 && "text-emerald-700",
                )}
              >
                {entry.amount.minorUnits > 0 ? "+" : ""}
                {format.money(entry.amount)}
              </p>
              <p className="text-xs text-muted-foreground">
                {t("clientCard.balanceAfter", { amount: format.money(entry.balanceAfter) })}
              </p>
            </div>
          </li>
        ))}
      </ul>
      {mode !== null && (
        <ClientPaymentSheet
          api={api}
          branchId={branchId}
          clientId={client.id}
          summary={summary}
          mode={mode}
          open
          onOpenChange={(open) => {
            if (!open) setMode(null);
          }}
          onSaved={async () => {
            const updated = await api.call("clients/detail", { id: client.id });
            if (updated.ok) onChanged(updated.value);
          }}
        />
      )}
      <SaleSheet
        api={api}
        branchId={branchId}
        saleId={saleId}
        open={saleId !== null}
        onOpenChange={(open) => {
          if (!open) setSaleId(null);
        }}
      />
    </EditSheetBody>
  );
}
