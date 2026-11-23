import { useForm } from "@tanstack/react-form";
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
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, SelectField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery, myBranchesQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { requiredText } from "@/forms/requiredText";

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
                        if (result.ok) await refresh();
                        else setError(apiErrorMessage(t, result.error));
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

/**
 * Форма кассы: новая — с начальным остатком на дату, существующая [initial] — только название,
 * вид и филиал. Остаток может быть отрицательным (ведущий минус) в валюте [currency].
 */
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
  const { close } = useEditSheet();
  const branches = useQuery(myBranchesQuery(api));
  const [id] = useState(() => initial?.id ?? CashAccountIdSchema.parse(uuidv7()));
  const today: string = todayLocalDate(useSession(api).orgInfo.timezone);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      kind: initial?.kind ?? "CASH",
      branch: initial?.branchId ?? "",
      opening: "0",
      date: today,
    },
    onSubmit: async ({ value }) => {
      const amount = parseMoney(value.opening.trim().replace(/^-/, ""), currency);
      const on = LocalDateSchema.safeParse(value.date);
      const branchId = BranchIdSchema.safeParse(value.branch);
      if (initial === null && (amount === null || !on.success)) {
        setFailure(t("error.invalidAmount"));
        return;
      }
      setFailure(null);
      const result = await api.call("finance/cash-accounts/save", {
        id,
        name: value.name.trim(),
        kind: value.kind,
        branchId: branchId.success ? branchId.data : null,
        openingBalance:
          initial === null && amount !== null
            ? {
                ...amount,
                minorUnits: value.opening.trim().startsWith("-")
                  ? -amount.minorUnits
                  : amount.minorUnits,
              }
            : null,
        openingOn: initial === null && on.success ? on.data : null,
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
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name" validators={{ onSubmit: requiredText(t) }}>
            {(field) => <TextField field={field} label={t("finance.name")} autoComplete="off" />}
          </form.Field>
          <form.Field name="kind">
            {(field) => (
              <SelectField
                field={field}
                label={t("finance.kind")}
                options={CashAccountKindSchema.options.map((k) => ({
                  value: k,
                  label: t(`finance.accountKind.${k}`),
                }))}
              />
            )}
          </form.Field>
          <form.Field name="branch">
            {(field) => (
              <SelectField
                field={field}
                label={t("finance.branch")}
                options={[
                  { value: "", label: t("finance.noBranch") },
                  ...(branches.data?.branches ?? []).map((b) => ({ value: b.id, label: b.name })),
                ]}
              />
            )}
          </form.Field>
          {initial === null && (
            <>
              <form.Field name="opening">
                {(field) => (
                  <TextField
                    field={field}
                    label={t("finance.openingBalance")}
                    inputMode="decimal"
                    autoComplete="off"
                  />
                )}
              </form.Field>
              <form.Field name="date">
                {(field) => (
                  <TextField field={field} label={t("finance.date")} type="date" max={today} />
                )}
              </form.Field>
            </>
          )}
        </EditSheetForm>
      )}
    </form.Subscribe>
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
                  if (result.ok) await refresh();
                  else setError(apiErrorMessage(t, result.error));
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

/**
 * Форма статьи ДДС: новая или существующая [initial]. Родителем может быть только
 * корневая несистемная группа того же направления из [articles]; группа родителя не имеет.
 */
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
  const { close } = useEditSheet();
  const [id] = useState(() => initial?.id ?? CashArticleIdSchema.parse(uuidv7()));
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      direction: initial?.direction ?? "EXPENSE",
      parent: initial?.parentId ?? "",
      group: initial?.isGroup ?? false,
    },
    onSubmit: async ({ value }) => {
      setFailure(null);
      const parentId = CashArticleIdSchema.safeParse(value.parent);
      const result = await api.call("finance/cash-articles/save", {
        id,
        name: value.name.trim(),
        direction: value.direction,
        parentId: !value.group && parentId.success ? parentId.data : null,
        isGroup: value.group,
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
        direction: state.values.direction,
        group: state.values.group,
      })}
    >
      {({ dirty, submitting, direction, group }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name" validators={{ onSubmit: requiredText(t) }}>
            {(field) => <TextField field={field} label={t("finance.name")} autoComplete="off" />}
          </form.Field>
          <form.Field
            name="direction"
            listeners={{
              onChange: () => {
                form.setFieldValue("parent", "");
              },
            }}
          >
            {(field) => (
              <SelectField
                field={field}
                label={t("finance.direction")}
                options={CashDirectionSchema.options.map((d) => ({
                  value: d,
                  label: t(`finance.direction.${d}`),
                }))}
              />
            )}
          </form.Field>
          <form.Field
            name="group"
            listeners={{
              onChange: () => {
                form.setFieldValue("parent", "");
              },
            }}
          >
            {(field) => <CheckboxField field={field} label={t("finance.isGroup")} />}
          </form.Field>
          {!group && (
            <form.Field name="parent">
              {(field) => (
                <SelectField
                  field={field}
                  label={t("finance.parent")}
                  options={[
                    { value: "", label: t("finance.noParent") },
                    ...articles
                      .filter(
                        (a) =>
                          a.isGroup &&
                          a.parentId === null &&
                          !a.archived &&
                          a.systemCode === null &&
                          a.direction === direction &&
                          a.id !== id,
                      )
                      .map((a) => ({ value: a.id, label: a.name })),
                  ]}
                />
              )}
            </form.Field>
          )}
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
