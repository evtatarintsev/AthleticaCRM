import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  BranchIdSchema,
  CashAccountIdSchema,
  CashAccountKindSchema,
  CashArticleIdSchema,
  CashDirectionSchema,
  LocalDateSchema,
  type BranchId,
  type CashAccountSchema,
  type CashArticleSchema,
  type Currency,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery, myBranchesQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet } from "@/ui/EditSheet";
import { FinanceForm } from "./FinanceForm";

export function FinanceAccounts({
  api,
  branchId,
  canManage,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly canManage: boolean;
}) {
  const { t, format } = useI18n();
  const data = useQuery(apiQuery(api, branchId, "finance/cash-accounts"));
  const branches = useQuery(myBranchesQuery(api));
  const queries = useQueryClient();
  const [editing, setEditing] = useState<CashAccountSchema | null>(null);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const refresh = async () => {
    await queries.invalidateQueries({ queryKey: ["api", branchId] });
  };
  const rows = (archived: boolean) => (
    <ul className="divide-y rounded-lg border">
      {data.data?.accounts
        .filter((a) => a.archived === archived)
        .map((a) => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
            <div>
              <p className="font-medium">{a.name}</p>
              <p className="text-sm text-muted-foreground">
                {t(`finance.accountKind.${a.kind}`)} ·{" "}
                {branches.data?.branches.find((b) => b.id === a.branchId)?.name ??
                  t("finance.noBranch")}
              </p>
              <p className="tabular-nums">{format.money(a.balance)}</p>
            </div>
            {canManage && (
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setEditing(a);
                    setOpen(true);
                  }}
                >
                  {t("finance.edit")}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    void api
                      .call("finance/cash-accounts/archive", { id: a.id, archived: !a.archived })
                      .then(async (result) => {
                        if (result.ok) {
                          await refresh();
                        } else {
                          setError(apiErrorMessage(t, result.error));
                        }
                      });
                  }}
                >
                  {t(a.archived ? "finance.restore" : "finance.archive")}
                </Button>
              </div>
            )}
          </li>
        ))}
    </ul>
  );
  return (
    <div className="space-y-4">
      {canManage && (
        <Button
          type="button"
          onClick={() => {
            setEditing(null);
            setOpen(true);
          }}
        >
          {t("finance.createAccount")}
        </Button>
      )}
      {(data.isError || error !== null) && <FormAlert message={error ?? t("finance.loadError")} />}
      {data.data !== undefined && (
        <p className="text-xl font-semibold">
          {t("finance.total")}: {format.money(data.data.total)}
        </p>
      )}
      {rows(false)}
      <details>
        <summary className="cursor-pointer">{t("finance.archived")}</summary>
        {rows(true)}
      </details>
      <EditSheet
        open={open}
        onOpenChange={setOpen}
        title={t(editing === null ? "finance.createAccount" : "finance.edit")}
      >
        <CashAccountForm
          api={api}
          initial={editing}
          currency={data.data?.total.currency ?? "RUB"}
          onSaved={refresh}
        />
      </EditSheet>
    </div>
  );
}

function CashAccountForm({
  api,
  initial,
  currency,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly initial: CashAccountSchema | null;
  readonly currency: Currency;
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const branches = useQuery(myBranchesQuery(api));
  const [id] = useState(() => initial?.id ?? CashAccountIdSchema.parse(uuidv7()));
  const [name, setName] = useState(initial?.name ?? "");
  const [kind, setKind] = useState(initial?.kind ?? "CASH");
  const [branch, setBranch] = useState<string>(initial?.branchId ?? "");
  const [opening, setOpening] = useState("0");
  const today = todayLocalDate(useSession(api).orgInfo.timezone);
  const [date, setDate] = useState<string>(today);
  const [invalid, setInvalid] = useState(false);
  return (
    <FinanceForm
      dirty={
        name !== (initial?.name ?? "") ||
        kind !== (initial?.kind ?? "CASH") ||
        branch !== (initial?.branchId ?? "") ||
        opening !== "0" ||
        date !== today
      }
      onSaved={onSaved}
      submit={() => {
        setInvalid(true);
        const amount = parseMoney(opening.trim().replace(/^-/, ""), currency);
        const on = LocalDateSchema.safeParse(date);
        const branchId = BranchIdSchema.safeParse(branch);
        if (name.trim() === "" || (initial === null && (amount === null || !on.success))) {
          return Promise.resolve(null);
        }
        return api.call("finance/cash-accounts/save", {
          id,
          name: name.trim(),
          kind,
          branchId: branchId.success ? branchId.data : null,
          openingBalance:
            initial === null && amount !== null
              ? {
                  ...amount,
                  minorUnits: opening.trim().startsWith("-")
                    ? -amount.minorUnits
                    : amount.minorUnits,
                }
              : null,
          openingOn: initial === null && on.success ? on.data : null,
        });
      }}
    >
      <label className="block space-y-1">
        <span>{t("finance.name")}</span>
        <Input
          value={name}
          aria-invalid={invalid && name.trim() === ""}
          onChange={(e) => {
            setName(e.target.value);
          }}
        />
        {invalid && name.trim() === "" && (
          <span className="text-sm text-destructive">{t("error.required")}</span>
        )}
      </label>
      <label className="block space-y-1">
        <span>{t("finance.kind")}</span>
        <NativeSelect
          value={kind}
          onChange={(e) => {
            setKind(CashAccountKindSchema.parse(e.target.value));
          }}
        >
          {CashAccountKindSchema.options.map((k) => (
            <option key={k} value={k}>
              {t(`finance.accountKind.${k}`)}
            </option>
          ))}
        </NativeSelect>
      </label>
      <label className="block space-y-1">
        <span>{t("finance.branch")}</span>
        <NativeSelect
          value={branch}
          onChange={(e) => {
            setBranch(e.target.value);
          }}
        >
          <option value="">{t("finance.noBranch")}</option>
          {branches.data?.branches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </NativeSelect>
      </label>
      {initial === null && (
        <>
          <label className="block space-y-1">
            <span>{t("finance.openingBalance")}</span>
            <Input
              inputMode="decimal"
              value={opening}
              onChange={(e) => {
                setOpening(e.target.value);
              }}
            />
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
        </>
      )}
    </FinanceForm>
  );
}

export function FinanceArticles({
  api,
  branchId,
  canManage,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly canManage: boolean;
}) {
  const { t } = useI18n();
  const data = useQuery(apiQuery(api, branchId, "finance/cash-articles"));
  const queries = useQueryClient();
  const [editing, setEditing] = useState<CashArticleSchema | null>(null);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const refresh = async () => {
    await queries.invalidateQueries({ queryKey: ["api", branchId] });
  };
  const articles = data.data?.articles ?? [];
  const row = (a: CashArticleSchema) => (
    <li
      key={a.id}
      className="flex flex-wrap items-center justify-between gap-2 border-b p-3 last:border-0"
    >
      <div>
        <p className={a.isGroup ? "font-semibold" : ""}>{a.name}</p>
        {a.parentId !== null && (
          <p className="text-xs text-muted-foreground">
            {articles.find((parent) => parent.id === a.parentId)?.name}
          </p>
        )}
        {a.systemCode !== null && (
          <p className="text-xs text-muted-foreground">{t("finance.system")}</p>
        )}
      </div>
      {canManage && a.systemCode === null && (
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setEditing(a);
              setOpen(true);
            }}
          >
            {t("finance.edit")}
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              void api
                .call("finance/cash-articles/archive", { id: a.id, archived: !a.archived })
                .then(async (result) => {
                  if (result.ok) {
                    await refresh();
                  } else {
                    setError(apiErrorMessage(t, result.error));
                  }
                });
            }}
          >
            {t(a.archived ? "finance.restore" : "finance.archive")}
          </Button>
        </div>
      )}
    </li>
  );
  return (
    <div className="space-y-4">
      {canManage && (
        <Button
          type="button"
          onClick={() => {
            setEditing(null);
            setOpen(true);
          }}
        >
          {t("finance.createArticle")}
        </Button>
      )}
      {(data.isError || error !== null) && <FormAlert message={error ?? t("finance.loadError")} />}
      <div className="grid gap-4 lg:grid-cols-2">
        {CashDirectionSchema.options.map((direction) => (
          <section key={direction} className="space-y-2">
            <h2 className="font-semibold">{t(`finance.direction.${direction}`)}</h2>
            <ul className="rounded-lg border">
              {articles
                .filter((a) => a.direction === direction && !a.archived && a.parentId === null)
                .flatMap((root) => [
                  row(root),
                  ...articles
                    .filter((child) => child.parentId === root.id && !child.archived)
                    .map(row),
                ])}
            </ul>
          </section>
        ))}
      </div>
      <details>
        <summary className="cursor-pointer">{t("finance.archived")}</summary>
        <ul className="rounded-lg border">{articles.filter((a) => a.archived).map(row)}</ul>
      </details>
      <EditSheet
        open={open}
        onOpenChange={setOpen}
        title={t(editing === null ? "finance.createArticle" : "finance.edit")}
      >
        <CashArticleForm api={api} initial={editing} articles={articles} onSaved={refresh} />
      </EditSheet>
    </div>
  );
}

function CashArticleForm({
  api,
  initial,
  articles,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly initial: CashArticleSchema | null;
  readonly articles: readonly CashArticleSchema[];
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const [id] = useState(() => initial?.id ?? CashArticleIdSchema.parse(uuidv7()));
  const [name, setName] = useState(initial?.name ?? "");
  const [direction, setDirection] = useState(initial?.direction ?? "EXPENSE");
  const [parent, setParent] = useState<string>(initial?.parentId ?? "");
  const [group, setGroup] = useState(initial?.isGroup ?? false);
  const [invalid, setInvalid] = useState(false);
  return (
    <FinanceForm
      dirty={
        name !== (initial?.name ?? "") ||
        direction !== (initial?.direction ?? "EXPENSE") ||
        parent !== (initial?.parentId ?? "") ||
        group !== (initial?.isGroup ?? false)
      }
      onSaved={onSaved}
      submit={() => {
        setInvalid(true);
        if (name.trim() === "") {
          return Promise.resolve(null);
        }
        const parentId = CashArticleIdSchema.safeParse(parent);
        return api.call("finance/cash-articles/save", {
          id,
          name: name.trim(),
          direction,
          parentId: !group && parentId.success ? parentId.data : null,
          isGroup: group,
        });
      }}
    >
      <label className="block space-y-1">
        <span>{t("finance.name")}</span>
        <Input
          value={name}
          aria-invalid={invalid && name.trim() === ""}
          onChange={(e) => {
            setName(e.target.value);
          }}
        />
        {invalid && name.trim() === "" && (
          <span className="text-sm text-destructive">{t("error.required")}</span>
        )}
      </label>
      <label className="block space-y-1">
        <span>{t("finance.direction")}</span>
        <NativeSelect
          value={direction}
          onChange={(e) => {
            setDirection(CashDirectionSchema.parse(e.target.value));
            setParent("");
          }}
        >
          {CashDirectionSchema.options.map((d) => (
            <option key={d} value={d}>
              {t(`finance.direction.${d}`)}
            </option>
          ))}
        </NativeSelect>
      </label>
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={group}
          onChange={(e) => {
            setGroup(e.target.checked);
            setParent("");
          }}
        />
        {t("finance.isGroup")}
      </label>
      {!group && (
        <label className="block space-y-1">
          <span>{t("finance.parent")}</span>
          <NativeSelect
            value={parent}
            onChange={(e) => {
              setParent(e.target.value);
            }}
          >
            <option value="">{t("finance.noParent")}</option>
            {articles
              .filter(
                (a) =>
                  a.isGroup &&
                  a.parentId === null &&
                  !a.archived &&
                  a.systemCode === null &&
                  a.direction === direction &&
                  a.id !== id,
              )
              .map((a) => (
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
