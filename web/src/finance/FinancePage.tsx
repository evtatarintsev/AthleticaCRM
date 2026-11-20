import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import { CashAccountIdSchema, LocalDateSchema, type BranchId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { downloadBlob } from "@/lib/download";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { FinanceAccounts, FinanceArticles } from "./FinanceCatalogs";
import { FinanceOperations } from "./FinanceOperations";
import { financePeriod, FinanceSearchSchema, type FinanceSearch } from "./financeSearch";

/** Раздел финансов не запрашивает данные при отсутствии права просмотра. */
export function FinancePage({
  api,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly search: FinanceSearch;
  readonly onSearchChange: (search: FinanceSearch) => void;
}) {
  const { t } = useI18n();
  const session = useSession(api);
  if (!hasPermission(session, "CAN_VIEW_FINANCE"))
    return (
      <section>
        <PageHeader title={t("finance.title")} />
        <p>{t("finance.noAccess")}</p>
      </section>
    );
  const branchId = session.currentBranch.id;
  const canManage = hasPermission(session, "CAN_MANAGE_FINANCE");
  return (
    <section className="min-w-0 max-w-7xl space-y-4">
      <PageHeader title={t("finance.title")} />
      <Tabs
        value={search.tab}
        onValueChange={(tab) => {
          onSearchChange(FinanceSearchSchema.parse({ ...search, tab }));
        }}
      >
        <TabsList className="max-w-full flex-wrap">
          {(["operations", "cashFlow", "accounts", "articles"] as const).map((tab) => (
            <TabsTrigger key={tab} value={tab}>
              {t(`finance.${tab}`)}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="operations">
          <FinanceOperations
            api={api}
            branchId={branchId}
            search={search}
            onSearchChange={onSearchChange}
            canManage={canManage}
          />
        </TabsContent>
        <TabsContent value="cashFlow">
          <FinanceCashFlow
            api={api}
            branchId={branchId}
            search={search}
            onSearchChange={onSearchChange}
          />
        </TabsContent>
        <TabsContent value="accounts">
          <FinanceAccounts api={api} branchId={branchId} canManage={canManage} />
        </TabsContent>
        <TabsContent value="articles">
          <FinanceArticles api={api} branchId={branchId} canManage={canManage} />
        </TabsContent>
      </Tabs>
    </section>
  );
}

function FinanceCashFlow({
  api,
  branchId,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly search: FinanceSearch;
  readonly onSearchChange: (search: FinanceSearch) => void;
}) {
  const { t, format } = useI18n();
  const period = financePeriod(search);
  const accounts = useQuery(apiQuery(api, branchId, "finance/cash-accounts"));
  const query = { ...period, cashAccountIds: search.cash };
  const data = useQuery(apiQuery(api, branchId, "finance/cash-flow", query));
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  return (
    <div className="space-y-4">
      <div className="grid gap-3 rounded-lg border p-3 sm:grid-cols-3">
        <label className="block space-y-1">
          <span>{t("finance.from")}</span>
          <Input
            type="date"
            value={period.from}
            onChange={(e) => {
              onSearchChange({ ...search, from: LocalDateSchema.safeParse(e.target.value).data });
            }}
          />
        </label>
        <label className="block space-y-1">
          <span>{t("finance.to")}</span>
          <Input
            type="date"
            value={period.to}
            onChange={(e) => {
              onSearchChange({ ...search, to: LocalDateSchema.safeParse(e.target.value).data });
            }}
          />
        </label>
        <label className="block space-y-1">
          <span>{t("finance.allAccounts")}</span>
          <NativeSelect
            multiple
            className="h-24"
            value={search.cash}
            onChange={(e) => {
              onSearchChange({
                ...search,
                cash: Array.from(e.target.selectedOptions, (o) =>
                  CashAccountIdSchema.parse(o.value),
                ),
              });
            }}
          >
            {accounts.data?.accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </NativeSelect>
        </label>
      </div>
      <Button
        type="button"
        variant="outline"
        disabled={exporting}
        onClick={() => {
          setExporting(true);
          void api.call("finance/cash-flow/export", query).then((result) => {
            setExporting(false);
            if (result.ok) downloadBlob("cash-flow.csv", result.value);
            else setError(apiErrorMessage(t, result.error));
          });
        }}
      >
        {t("finance.export")}
      </Button>
      {(data.isError || error !== null) && <FormAlert message={error ?? t("finance.loadError")} />}
      {data.isPending && <Skeleton className="h-40" />}
      {data.data !== undefined && (
        <>
          <dl className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt>{t("finance.openingAtStart")}</dt>
              <dd className="font-semibold">{format.money(data.data.opening)}</dd>
            </div>
            <div>
              <dt>{t("finance.receipts")}</dt>
              <dd className="font-semibold">{format.money(data.data.receipts)}</dd>
            </div>
            <div>
              <dt>{t("finance.expenses")}</dt>
              <dd className="font-semibold">{format.money(data.data.expenses)}</dd>
            </div>
            <div>
              <dt>{t("finance.closing")}</dt>
              <dd className="font-semibold">{format.money(data.data.closing)}</dd>
            </div>
          </dl>
          <p>
            {t("finance.flow")}:{" "}
            {format.money({
              ...data.data.receipts,
              minorUnits: data.data.receipts.minorUnits - data.data.expenses.minorUnits,
            })}
          </p>
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-left text-sm">
              <thead>
                <tr>
                  <th className="p-3">{t("finance.articles")}</th>
                  {data.data.months.map((month) => (
                    <th key={month} className="p-3 text-right">
                      {month}
                    </th>
                  ))}
                  <th className="p-3 text-right">{t("finance.total")}</th>
                </tr>
              </thead>
              <tbody>
                {data.data.rows.map((row) => (
                  <tr
                    key={`${row.kind}-${row.articleId ?? ""}`}
                    className={
                      row.kind === "group" ? "border-t bg-muted font-semibold" : "border-t"
                    }
                  >
                    <th className="p-3 font-normal">
                      {row.kind === "opening"
                        ? t("finance.opening")
                        : row.kind === "transfer"
                          ? t("finance.movements")
                          : row.parentName === null
                            ? row.name
                            : `${row.parentName} / ${row.name}`}
                    </th>
                    {row.amounts.map((amount, index) => (
                      <td key={String(index)} className="p-3 text-right tabular-nums">
                        {row.articleId !== null && row.kind === "article" ? (
                          <Button
                            type="button"
                            variant="link"
                            className="h-auto p-0"
                            onClick={() => {
                              const month = data.data.months[index];
                              if (month === undefined) return;
                              const start = LocalDateSchema.safeParse(`${month}-01`);
                              if (!start.success) return;
                              const end = LocalDateSchema.parse(
                                new Date(
                                  Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5)), 0),
                                )
                                  .toISOString()
                                  .slice(0, 10),
                              );
                              onSearchChange({
                                ...search,
                                tab: "operations",
                                article: row.articleId === null ? [] : [row.articleId],
                                from: start.data < period.from ? period.from : start.data,
                                to: end > period.to ? period.to : end,
                                kind: undefined,
                              });
                            }}
                          >
                            {format.money(amount)}
                          </Button>
                        ) : (
                          format.money(amount)
                        )}
                      </td>
                    ))}
                    <td className="p-3 text-right font-medium tabular-nums">
                      {format.money(row.total)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
