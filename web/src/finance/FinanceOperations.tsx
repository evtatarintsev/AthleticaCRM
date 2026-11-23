import { skipToken, useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  CashAccountIdSchema,
  CashArticleIdSchema,
  EmployeeIdSchema,
  LedgerKindSchema,
  LocalDateSchema,
  type BranchId,
  type CashAccountSchema,
  type CashArticleSchema,
  type CashJournalEntry,
  type ClientId,
  type Currency,
  type SaleId,
} from "@/api/generated/contracts";
import { ClientPickerSheet } from "@/clients/ClientPickerSheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { downloadBlob } from "@/lib/download";
import { todayLocalDate } from "@/lib/localDate";
import { parseMoney } from "@/lib/money";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { unwrap } from "@/query/apiFailure";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { CancellationForm } from "./CancellationForm";
import { FinanceForm } from "./FinanceForm";
import { financePeriod, type FinanceSearch } from "./financeSearch";
import { SaleSheet } from "./SaleSheet";

interface OperationsProps {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly search: FinanceSearch;
  readonly onSearchChange: (search: FinanceSearch) => void;
  readonly canManage: boolean;
}

/** Постраничный журнал с фильтрами в адресе и отдельными карточками на телефоне. */
export function FinanceOperations({
  api,
  branchId,
  search,
  onSearchChange,
  canManage,
}: OperationsProps) {
  const { t, format } = useI18n();
  const queries = useQueryClient();
  const accounts = useQuery(apiQuery(api, branchId, "finance/cash-accounts"));
  const articles = useQuery(apiQuery(api, branchId, "finance/cash-articles"));
  const request = {
    ...financePeriod(search, useSession(api).orgInfo.timezone),
    cashAccountIds: search.cash,
    articleIds: search.article,
    kinds: search.kind === undefined ? [] : [search.kind],
    clientId: search.client ?? null,
    employeeId: search.author ?? null,
    includeReversed: search.includeReversed,
  };
  const data = useInfiniteQuery({
    queryKey: ["api", branchId, "finance/journal", request],
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(await api.call("finance/journal", { ...request, limit: 50, offset: pageParam })),
    getNextPageParam: (last, pages) => {
      const loaded = pages.reduce((count, page) => count + page.entries.length, 0);
      return loaded < last.count ? loaded : undefined;
    },
  });
  const [creating, setCreating] = useState<"income" | "expense" | "transfer" | null>(null);
  const [selection, setSelected] = useState<Pick<CashJournalEntry, "id" | "cashAccountId"> | null>(
    null,
  );
  const [cancelling, setCancelling] = useState(false);
  const [saleId, setSaleId] = useState<SaleId | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const entries = data.data?.pages.flatMap((page) => page.entries) ?? [];
  const selected =
    entries.find(
      (entry) => entry.id === selection?.id && entry.cashAccountId === selection.cashAccountId,
    ) ?? null;
  const first = data.data?.pages[0];
  const refresh = async () => {
    await queries.invalidateQueries({ queryKey: ["api", branchId] });
  };
  return (
    <div className="space-y-4">
      <JournalFilters
        api={api}
        branchId={branchId}
        search={search}
        onSearchChange={onSearchChange}
        accounts={accounts.data?.accounts ?? []}
        articles={articles.data?.articles ?? []}
      />
      <div className="flex flex-wrap gap-2">
        {canManage &&
          (["income", "expense", "transfer"] as const).map((kind) => (
            <Button
              key={kind}
              type="button"
              disabled={accounts.data === undefined || articles.data === undefined}
              onClick={() => {
                setCreating(kind);
              }}
            >
              {t(`finance.${kind}`)}
            </Button>
          ))}
        <Button
          type="button"
          variant="outline"
          disabled={exporting}
          onClick={() => {
            setExporting(true);
            void api
              .call("finance/journal/export", { ...request, limit: 50, offset: 0 })
              .then((result) => {
                setExporting(false);
                if (result.ok) {
                  downloadBlob("cash-journal.csv", result.value);
                } else {
                  setError(apiErrorMessage(t, result.error));
                }
              });
          }}
        >
          {t("finance.export")}
        </Button>
      </div>
      {first !== undefined && (
        <div className="flex flex-wrap gap-6 rounded-lg border p-4">
          <p>
            {t("finance.receipts")}: <strong>{format.money(first.receipts)}</strong>
          </p>
          <p>
            {t("finance.expenses")}: <strong>{format.money(first.expenses)}</strong>
          </p>
        </div>
      )}
      {(data.isError || error !== null) && <FormAlert message={error ?? t("finance.loadError")} />}
      {data.isPending && <Skeleton className="h-40" />}
      {data.isSuccess && entries.length === 0 && (
        <p className="py-10 text-center text-muted-foreground">{t("finance.empty")}</p>
      )}
      <div className="hidden md:block">
        <table className="w-full text-left text-sm">
          <thead>
            <tr>
              {["date", "kind", "cashAccount", "articles", "amount"].map((key) => (
                <th key={key} className="p-3">
                  {key === "date"
                    ? t("finance.date")
                    : key === "kind"
                      ? t("finance.kind")
                      : key === "cashAccount"
                        ? t("finance.cashAccount")
                        : key === "articles"
                          ? t("finance.articles")
                          : t("finance.amount")}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {entries.map((entry, index) => (
              <tr key={`${entry.id}-${String(index)}`} className="border-t">
                <td className="p-3">{format.date(entry.occurredOn)}</td>
                <td className="p-3">
                  <Button
                    type="button"
                    variant="link"
                    className="h-auto p-0"
                    onClick={() => {
                      setSelected(entry);
                    }}
                  >
                    {t(`finance.kind.${entry.kind}`)}
                  </Button>
                  {entry.reversed && <p>{t("finance.cancelled")}</p>}
                </td>
                <td className="p-3">
                  {entry.cashAccountName}
                  {entry.counterCashAccountName !== null && ` / ${entry.counterCashAccountName}`}
                </td>
                <td className="p-3">{entry.articleName}</td>
                <td className="p-3 tabular-nums">{format.money(entry.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="space-y-3 md:hidden">
        {entries.map((entry, index) => (
          <li
            key={`${entry.id}-${String(index)}`}
            className="min-w-0 space-y-2 rounded-lg border p-3"
          >
            <Button
              type="button"
              variant="link"
              className="h-auto p-0"
              onClick={() => {
                setSelected(entry);
              }}
            >
              {t(`finance.kind.${entry.kind}`)}
            </Button>
            <p className="font-semibold tabular-nums">{format.money(entry.amount)}</p>
            <p className="break-words text-sm">
              {entry.cashAccountName}
              {entry.counterCashAccountName !== null && ` / ${entry.counterCashAccountName}`} ·{" "}
              {entry.articleName}
            </p>
            <p className="text-xs text-muted-foreground">
              {format.date(entry.occurredOn)} · {entry.clientName ?? entry.counterparty}
            </p>
            {entry.reversed && <p>{t("finance.cancelled")}</p>}
          </li>
        ))}
      </ul>
      {data.hasNextPage && (
        <Button
          type="button"
          variant="outline"
          disabled={data.isFetchingNextPage}
          onClick={() => {
            void data.fetchNextPage();
          }}
        >
          {t("finance.more")}
        </Button>
      )}
      <EditSheet
        open={creating !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCreating(null);
          }
        }}
        title={creating === null ? "" : t(`finance.${creating}`)}
      >
        {creating !== null && (
          <CashOperationForm
            api={api}
            branchId={branchId}
            kind={creating}
            accounts={accounts.data?.accounts ?? []}
            articles={articles.data?.articles ?? []}
            currency={accounts.data?.total.currency ?? "RUB"}
            onSaved={refresh}
          />
        )}
      </EditSheet>
      <EditSheet
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelected(null);
            setCancelling(false);
          }
        }}
        title={t("finance.operations")}
      >
        <EditSheetBody>
          {selected !== null && (
            <>
              <p className="text-xl font-semibold">
                {t(`finance.kind.${selected.kind}`)} · {format.money(selected.amount)}
              </p>
              <p>
                {format.date(selected.occurredOn)} · {selected.cashAccountName}
                {selected.counterCashAccountName !== null &&
                  ` / ${selected.counterCashAccountName}`}
              </p>
              <p>{selected.articleName}</p>
              <p>
                {selected.performedBy?.name} · {selected.counterparty} · {selected.comment}
              </p>
              {selected.clientId !== null && (
                <Link to="/clients/$clientId" params={{ clientId: selected.clientId }}>
                  {selected.clientName}
                </Link>
              )}
              {selected.saleId !== null && (
                <Button
                  type="button"
                  variant="link"
                  onClick={() => {
                    setSaleId(selected.saleId);
                  }}
                >
                  {t("finance.sale")}
                </Button>
              )}
              {selected.reversed ? (
                <p>
                  {t("finance.cancelled")} · {selected.reversalReason} · {selected.reversedBy?.name}
                </p>
              ) : (
                selected.canReverse && (
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={() => {
                      setCancelling(true);
                    }}
                  >
                    {t("finance.cancelOperation")}
                  </Button>
                )
              )}
              <EditSheet
                open={cancelling}
                onOpenChange={setCancelling}
                title={t("finance.cancelOperation")}
              >
                <CancellationForm
                  api={api}
                  operation={{
                    kind:
                      selected.kind === "PAYMENT" || selected.kind === "PAYOUT"
                        ? "payment"
                        : "cash",
                    id: selected.id,
                  }}
                  onSaved={async () => {
                    setSelected(null);
                    await refresh();
                  }}
                />
              </EditSheet>
            </>
          )}
        </EditSheetBody>
      </EditSheet>
      <SaleSheet
        api={api}
        branchId={branchId}
        saleId={saleId}
        open={saleId !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSaleId(null);
          }
        }}
      />
    </div>
  );
}

export function JournalFilters({
  api,
  branchId,
  search,
  onSearchChange,
  accounts,
  articles,
}: Omit<OperationsProps, "canManage"> & {
  readonly accounts: readonly CashAccountSchema[];
  readonly articles: readonly CashArticleSchema[];
}) {
  const { t } = useI18n();
  const employees = useQuery(apiQuery(api, branchId, "employees/list"));
  const selectedClient = search.client;
  const client = useQuery({
    queryKey: ["api", branchId, "clients/detail", selectedClient],
    queryFn:
      selectedClient === undefined
        ? skipToken
        : async () => unwrap(await api.call("clients/detail", { id: selectedClient })),
  });
  const [picker, setPicker] = useState(false);
  const period = financePeriod(search, useSession(api).orgInfo.timezone);
  return (
    <div className="space-y-3 rounded-lg border p-3">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
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
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <label className="block space-y-1">
          <span>{t("finance.allArticles")}</span>
          <NativeSelect
            multiple
            className="h-24"
            value={search.article}
            onChange={(e) => {
              onSearchChange({
                ...search,
                article: Array.from(e.target.selectedOptions, (o) =>
                  CashArticleIdSchema.parse(o.value),
                ),
              });
            }}
          >
            {articles
              .filter((a) => !a.isGroup)
              .map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
          </NativeSelect>
        </label>
        <label className="block space-y-1">
          <span>{t("finance.kind")}</span>
          <NativeSelect
            value={search.kind ?? ""}
            onChange={(e) => {
              onSearchChange({ ...search, kind: LedgerKindSchema.safeParse(e.target.value).data });
            }}
          >
            <option value="">{t("finance.allKinds")}</option>
            {(["PAYMENT", "PAYOUT", "INCOME", "EXPENSE", "TRANSFER", "OPENING"] as const).map(
              (k) => (
                <option key={k} value={k}>
                  {t(`finance.kind.${k}`)}
                </option>
              ),
            )}
          </NativeSelect>
        </label>
        <label className="block space-y-1">
          <span>{t("finance.author")}</span>
          <NativeSelect
            value={search.author ?? ""}
            onChange={(e) => {
              onSearchChange({
                ...search,
                author: EmployeeIdSchema.safeParse(e.target.value).data,
              });
            }}
          >
            <option value="">{t("finance.select")}</option>
            {employees.data?.employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <div className="flex flex-wrap items-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setPicker(true);
            }}
          >
            {client.data?.name ?? t("finance.client")}
          </Button>
          {search.client !== undefined && (
            <Button
              type="button"
              variant="ghost"
              aria-label={t("finance.clearClient")}
              onClick={() => {
                onSearchChange({ ...search, client: undefined });
              }}
            >
              ×
            </Button>
          )}
        </div>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={search.includeReversed}
            onChange={(e) => {
              onSearchChange({ ...search, includeReversed: e.target.checked });
            }}
          />
          {t("finance.includeReversed")}
        </label>
      </div>
      <ClientPickerSheet
        api={api}
        branchId={branchId}
        open={picker}
        onOpenChange={setPicker}
        title={t("finance.client")}
        mode="single"
        unavailable={new Map<ClientId, string>()}
        onSubmit={(picked) => {
          const first = picked[0];
          if (first !== undefined) {
            onSearchChange({ ...search, client: first.id });
          }
          return Promise.resolve(null);
        }}
      />
    </div>
  );
}

function CashOperationForm({
  api,
  branchId,
  kind,
  accounts,
  articles,
  currency,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly kind: "income" | "expense" | "transfer";
  readonly accounts: readonly CashAccountSchema[];
  readonly articles: readonly CashArticleSchema[];
  readonly currency: Currency;
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const employees = useQuery(apiQuery(api, branchId, "employees/list"));
  const available = accounts.filter((a) => !a.archived);
  const selectable = articles.filter(
    (a) =>
      !a.archived &&
      !a.isGroup &&
      a.systemCode === null &&
      a.direction === (kind === "income" ? "INCOME" : "EXPENSE"),
  );
  const [cashId, setCashId] = useState<string>(available[0]?.id ?? "");
  const [toId, setToId] = useState<string>(available[1]?.id ?? "");
  const [articleId, setArticleId] = useState<string>(selectable[0]?.id ?? "");
  const [amount, setAmount] = useState("");
  const today = todayLocalDate(useSession(api).orgInfo.timezone);
  const [date, setDate] = useState<string>(today);
  const [counterparty, setCounterparty] = useState("");
  const [employee, setEmployee] = useState("");
  const [comment, setComment] = useState("");
  const [invalid, setInvalid] = useState(false);
  return (
    <FinanceForm
      dirty={
        amount !== "" ||
        counterparty !== "" ||
        employee !== "" ||
        comment !== "" ||
        date !== today ||
        cashId !== available[0]?.id ||
        toId !== available[1]?.id ||
        articleId !== selectable[0]?.id
      }
      onSaved={onSaved}
      submit={() => {
        setInvalid(true);
        const parsed = parseMoney(amount, currency);
        const cash = CashAccountIdSchema.safeParse(cashId);
        const to = CashAccountIdSchema.safeParse(toId);
        const article = CashArticleIdSchema.safeParse(articleId);
        const occurredOn = LocalDateSchema.safeParse(date);
        const person = EmployeeIdSchema.safeParse(employee);
        if (parsed === null || parsed.minorUnits <= 0 || !cash.success || !occurredOn.success) {
          return Promise.resolve(null);
        }
        if (kind === "transfer") {
          if (!to.success || to.data === cash.data) {
            return Promise.resolve(null);
          }
          return api.call("finance/transfer", {
            fromCashAccountId: cash.data,
            toCashAccountId: to.data,
            amount: parsed,
            occurredOn: occurredOn.data,
            comment: comment.trim() === "" ? null : comment.trim(),
          });
        }
        if (!article.success) {
          return Promise.resolve(null);
        }
        return api.call(kind === "income" ? "finance/income" : "finance/expense", {
          cashAccountId: cash.data,
          articleId: article.data,
          amount: parsed,
          occurredOn: occurredOn.data,
          counterpartyEmployeeId: person.success ? person.data : null,
          counterpartyText:
            person.success || counterparty.trim() === "" ? null : counterparty.trim(),
          comment: comment.trim() === "" ? null : comment.trim(),
        });
      }}
    >
      <label className="block space-y-1">
        <span>{t(kind === "transfer" ? "finance.fromCash" : "finance.cashAccount")}</span>
        <NativeSelect
          value={cashId}
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
      {kind === "transfer" ? (
        <label className="block space-y-1">
          <span>{t("finance.toCash")}</span>
          <NativeSelect
            value={toId}
            onChange={(e) => {
              setToId(e.target.value);
            }}
          >
            <option value="">{t("finance.select")}</option>
            {available
              .filter((a) => a.id !== cashId)
              .map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
          </NativeSelect>
        </label>
      ) : (
        <label className="block space-y-1">
          <span>{t("finance.articles")}</span>
          <NativeSelect
            value={articleId}
            onChange={(e) => {
              setArticleId(e.target.value);
            }}
          >
            <option value="">{t("finance.select")}</option>
            {selectable.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </NativeSelect>
        </label>
      )}
      <label className="block space-y-1">
        <span>{t("finance.amount")}</span>
        <Input
          value={amount}
          inputMode="decimal"
          aria-invalid={invalid && (parseMoney(amount, currency)?.minorUnits ?? 0) <= 0}
          onChange={(e) => {
            setAmount(e.target.value);
          }}
        />
        {invalid && (parseMoney(amount, currency)?.minorUnits ?? 0) <= 0 && (
          <span className="text-sm text-destructive">{t("error.invalidAmount")}</span>
        )}
      </label>
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
      {kind !== "transfer" && (
        <>
          <label className="block space-y-1">
            <span>{t("finance.counterparty")}</span>
            <Input
              value={counterparty}
              disabled={employee !== ""}
              onChange={(e) => {
                setCounterparty(e.target.value);
              }}
            />
          </label>
          <label className="block space-y-1">
            <span>{t("finance.counterpartyEmployee")}</span>
            <NativeSelect
              value={employee}
              onChange={(e) => {
                setEmployee(e.target.value);
              }}
            >
              <option value="">{t("finance.select")}</option>
              {employees.data?.employees
                .filter((e) => e.status !== "BLOCKED")
                .map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.name}
                  </option>
                ))}
            </NativeSelect>
          </label>
        </>
      )}
      <label className="block space-y-1">
        <span>{t("finance.comment")}</span>
        <Input
          value={comment}
          onChange={(e) => {
            setComment(e.target.value);
          }}
        />
      </label>
    </FinanceForm>
  );
}
