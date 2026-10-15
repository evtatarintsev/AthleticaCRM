import { useForm, useSelector } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronRightIcon, PencilIcon, PlusIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  MembershipIdSchema,
  TariffPlanIdSchema,
  type BranchId,
  type ClientId,
  type Currency,
  type DurationUnit,
  type TariffPlanSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { moneyEditText, parseMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { TariffRow } from "@/tariffs/TariffRow";
import { TariffSheet, type TariffTerms } from "@/tariffs/TariffSheet";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { membershipsQuery } from "./card/clientCardQueries";

/** Шаблон абонемента: тариф, из которого берутся условия, или индивидуальные условия. */
type Template =
  { readonly kind: "tariff"; readonly tariff: TariffPlanSchema } | { readonly kind: "individual" };

/**
 * Что редактируется во вложенной панели тарифа: новый тариф или существующий. Значение
 * остаётся после закрытия панели, чтобы её содержимое не пропадало во время анимации закрытия.
 */
type Editing =
  { readonly kind: "create" } | { readonly kind: "edit"; readonly tariff: TariffPlanSchema };

/** Значения формы условий абонемента: числа и сумма вводятся текстом. */
interface IssueSubscriptionFormValues {
  readonly unlimited: boolean;
  readonly sessions: string;
  readonly durationValue: string;
  readonly durationUnit: DurationUnit;
  readonly startDate: string;
  readonly price: string;
}

/** Наибольшее значение `Int` в Kotlin: больше сервер не примет. */
const MAX_INT32 = 2_147_483_647;

/** Положительное целое из текста [text] в пределах `Int`, иначе `null`. */
function positiveInt(text: string): number | null {
  const value = /^\d+$/.test(text.trim()) ? Number(text.trim()) : Number.NaN;
  return Number.isInteger(value) && value > 0 && value <= MAX_INT32 ? value : null;
}

/** Схема формы условий с сообщениями на языке [t]; сумма разбирается в валюте [currency]. */
function issueSubscriptionSchema(t: I18n["t"], currency: Currency) {
  return z
    .object({
      unlimited: z.boolean(),
      sessions: z.string(),
      durationValue: z.string().refine((v) => positiveInt(v) !== null, t("error.positiveInteger")),
      durationUnit: z.enum(["DAYS", "MONTHS"]),
      startDate: z.string().min(1, t("error.required")),
      price: z.string().refine((v) => parseMoney(v, currency) !== null, t("error.invalidAmount")),
    })
    .refine((v) => v.unlimited || positiveInt(v.sessions) !== null, {
      path: ["sessions"],
      message: t("error.positiveInteger"),
    });
}

/** Ключ шаблона [template]: одинаков у повторного выбора того же тарифа или индивидуального. */
function templateKey(template: Template): string {
  switch (template.kind) {
    case "tariff":
      return template.tariff.id;
    case "individual":
      return "individual";
  }
}

/** Значения формы по шаблону [template]; дата начала — сегодня. */
function valuesFrom(template: Template): IssueSubscriptionFormValues {
  const startDate = todayLocalDate();
  switch (template.kind) {
    case "tariff":
      return {
        unlimited: template.tariff.sessions === null,
        sessions: template.tariff.sessions?.toString() ?? "",
        durationValue: template.tariff.durationValue.toString(),
        durationUnit: template.tariff.durationUnit,
        startDate,
        price: moneyEditText(template.tariff.price),
      };
    case "individual":
      return {
        unlimited: false,
        sessions: "",
        durationValue: "1",
        durationUnit: "MONTHS",
        startDate,
        price: "",
      };
  }
}

/**
 * Панель выдачи абонемента клиенту [clientId], открытая при [open]: сначала выбор тарифа-шаблона
 * (с созданием и изменением тарифа поверх), затем условия абонемента. Тариф — только шаблон:
 * все условия можно изменить перед выдачей.
 */
export function IssueSubscriptionSheet({
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
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("clients.detail.issueSubscription")}
    >
      <IssueSubscriptionPanel api={api} branchId={branchId} clientId={clientId} />
    </EditSheet>
  );
}

/** Загружает валюту организации и показывает шаги выдачи, когда она известна. */
function IssueSubscriptionPanel({
  api,
  branchId,
  clientId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const settings = useQuery(apiQuery(api, branchId, "org/settings"));
  if (settings.isError) {
    return (
      <EditSheetBody>
        <FormAlert message={t("directory.loadError")} />
      </EditSheetBody>
    );
  }
  if (settings.data === undefined) {
    return (
      <EditSheetBody>
        <Skeleton className="h-32 w-full" />
      </EditSheetBody>
    );
  }
  return (
    <IssueSubscriptionSteps
      api={api}
      branchId={branchId}
      clientId={clientId}
      currency={settings.data.currency}
    />
  );
}

/**
 * Шаги выдачи в валюте [currency]. Форма условий живёт на обоих шагах: повторный выбор того же
 * шаблона сохраняет введённые значения, выбор другого заполняет поля его условиями заново.
 */
function IssueSubscriptionSteps({
  api,
  branchId,
  clientId,
  currency,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly currency: Currency;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close, reportGuard } = useEditSheet();
  const tariffsQuery = apiQuery(api, branchId, "tariffs/list", {});
  const tariffs = useQuery(tariffsQuery);
  const schema = useMemo(() => issueSubscriptionSchema(t, currency), [t, currency]);
  const [step, setStep] = useState<"pick" | "terms">("pick");
  const [chosen, setChosen] = useState<Template | null>(null);
  const [defaults, setDefaults] = useState(() => valuesFrom({ kind: "individual" }));
  const [failure, setFailure] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing>({ kind: "create" });
  const [editorOpen, setEditorOpen] = useState(false);

  const active = (tariffs.data?.tariffs ?? []).filter((tariff) => !tariff.archived);

  const form = useForm({
    defaultValues: defaults,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const durationValue = positiveInt(value.durationValue);
      const price = parseMoney(value.price, currency);
      const sessions = value.unlimited ? null : positiveInt(value.sessions);
      if (
        chosen === null ||
        durationValue === null ||
        price === null ||
        (!value.unlimited && sessions === null)
      ) {
        return;
      }
      setFailure(null);
      const result = await api.call("memberships/issue", {
        id: MembershipIdSchema.parse(uuidv7()),
        clientId,
        tariffPlanId: chosen.kind === "tariff" ? chosen.tariff.id : null,
        name:
          chosen.kind === "tariff"
            ? chosen.tariff.name
            : t("clients.detail.subscriptionIndividual"),
        sessions,
        durationValue,
        durationUnit: value.durationUnit,
        startDate: LocalDateSchema.parse(value.startDate),
        price,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: membershipsQuery(api, branchId, clientId).queryKey,
        }),
        queryClient.invalidateQueries({
          queryKey: apiQuery(api, branchId, "clients/detail", { id: clientId }).queryKey,
        }),
      ]);
      close();
    },
  });

  const dirty = useSelector(form.store, (state) => state.isDirty);
  useEffect(() => {
    if (step === "pick") {
      reportGuard({ dirty, submitting: false });
    }
  }, [reportGuard, step, dirty]);

  const choose = (template: Template) => {
    if (chosen === null || templateKey(chosen) !== templateKey(template)) {
      const values = valuesFrom(template);
      setDefaults(values);
      form.reset(values);
      setFailure(null);
    }
    setChosen(template);
    setStep("terms");
  };

  const refreshTariffs = () => queryClient.invalidateQueries({ queryKey: tariffsQuery.queryKey });

  const saveTariff = async (terms: TariffTerms): Promise<string | null> => {
    if (editing.kind === "edit") {
      const result = await api.call("tariffs/update", { id: editing.tariff.id, ...terms });
      if (!result.ok) {
        return apiErrorMessage(t, result.error);
      }
      await refreshTariffs();
      return null;
    }
    const id = TariffPlanIdSchema.parse(uuidv7());
    const result = await api.call("tariffs/create", { id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refreshTariffs();
    choose({ kind: "tariff", tariff: { id, ...terms, archived: false } });
    return null;
  };

  const openEditor = (next: Editing) => {
    setEditing(next);
    setEditorOpen(true);
  };

  const chosenKey = chosen === null ? null : templateKey(chosen);
  return (
    <>
      {step === "pick" && (
        <EditSheetBody>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-muted-foreground">{t("clients.issue.pickHint")}</p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                openEditor({ kind: "create" });
              }}
            >
              <PlusIcon aria-hidden />
              {t("tariffs.create")}
            </Button>
          </div>
          {tariffs.isError && <FormAlert message={t("directory.loadError")} />}
          {tariffs.isPending && <Skeleton className="h-32 w-full" />}
          <ul className="space-y-2">
            {active.map((tariff) => (
              <li
                key={tariff.id}
                className={cn(
                  "flex items-center rounded-lg border bg-card hover:bg-accent/40",
                  chosenKey === tariff.id && "border-primary ring-1 ring-primary",
                )}
              >
                <button
                  type="button"
                  aria-current={chosenKey === tariff.id ? "true" : undefined}
                  onClick={() => {
                    choose({ kind: "tariff", tariff });
                  }}
                  className="min-w-0 flex-1 rounded-lg p-4 text-left outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
                >
                  <TariffRow tariff={tariff} />
                </button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="mr-2 shrink-0"
                  aria-label={t("directory.edit", { name: tariff.name })}
                  onClick={() => {
                    openEditor({ kind: "edit", tariff });
                  }}
                >
                  <PencilIcon aria-hidden />
                </Button>
              </li>
            ))}
            <li
              className={cn(
                "rounded-lg border border-dashed bg-card hover:bg-accent/40",
                chosenKey === "individual" && "border-solid border-primary ring-1 ring-primary",
              )}
            >
              <button
                type="button"
                aria-current={chosenKey === "individual" ? "true" : undefined}
                onClick={() => {
                  choose({ kind: "individual" });
                }}
                className="flex w-full items-center gap-3 rounded-lg p-4 text-left outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
              >
                <span className="min-w-0 flex-1 space-y-0.5">
                  <span className="block font-medium">
                    {t("clients.detail.subscriptionIndividual")}
                  </span>
                  <span className="block text-sm text-muted-foreground">
                    {t("clients.issue.individualHint")}
                  </span>
                </span>
                <ChevronRightIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
              </button>
            </li>
          </ul>
        </EditSheetBody>
      )}
      {step === "terms" && chosen !== null && (
        <form.Subscribe
          selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
        >
          {({ dirty: formDirty, submitting }) => (
            <EditSheetForm
              dirty={formDirty}
              submitting={submitting}
              submitLabel={t("clients.issue.submit")}
              onSubmit={() => {
                void form.handleSubmit();
              }}
            >
              {failure !== null && <FormAlert message={failure} />}
              <TemplateSummary
                template={chosen}
                disabled={submitting}
                onChange={() => {
                  setStep("pick");
                }}
              />
              <form.Field name="unlimited">
                {(field) => (
                  <CheckboxField
                    field={field}
                    label={t("clients.detail.subscriptionUnlimitedLabel")}
                  />
                )}
              </form.Field>
              <form.Subscribe selector={(state) => state.values.unlimited}>
                {(unlimited) =>
                  !unlimited && (
                    <form.Field name="sessions">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("clients.detail.subscriptionSessionsField")}
                          inputMode="numeric"
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                  )
                }
              </form.Subscribe>
              <div className="grid gap-4 sm:grid-cols-2">
                <form.Field name="durationValue">
                  {(field) => (
                    <TextField
                      field={field}
                      label={t("clients.detail.subscriptionDuration")}
                      inputMode="numeric"
                      autoComplete="off"
                      required
                    />
                  )}
                </form.Field>
                <form.Field name="durationUnit">
                  {(field) => (
                    <RadioGroupField
                      field={field}
                      label={t("clients.detail.subscriptionDurationUnit")}
                      options={[
                        { value: "DAYS", label: t("tariffs.days") },
                        { value: "MONTHS", label: t("tariffs.months") },
                      ]}
                    />
                  )}
                </form.Field>
              </div>
              <form.Field name="startDate">
                {(field) => (
                  <TextField
                    field={field}
                    type="date"
                    label={t("clients.detail.subscriptionStartDate")}
                    autoComplete="off"
                    required
                  />
                )}
              </form.Field>
              <form.Field name="price">
                {(field) => (
                  <TextField
                    field={field}
                    label={t("clients.detail.subscriptionPrice", { currency })}
                    inputMode="decimal"
                    autoComplete="off"
                    required
                  />
                )}
              </form.Field>
            </EditSheetForm>
          )}
        </form.Subscribe>
      )}
      <TariffSheet
        open={editorOpen}
        onOpenChange={setEditorOpen}
        initial={
          editing.kind === "edit"
            ? (active.find((tariff) => tariff.id === editing.tariff.id) ?? editing.tariff)
            : null
        }
        currency={currency}
        onSave={saveTariff}
      />
    </>
  );
}

/** Выбранный шаблон [template] с кнопкой возврата к выбору [onChange]. */
function TemplateSummary({
  template,
  disabled,
  onChange,
}: {
  readonly template: Template;
  readonly disabled: boolean;
  readonly onChange: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-muted/40 p-3">
      <div className="min-w-0 flex-1">
        {template.kind === "tariff" ? (
          <TariffRow tariff={template.tariff} />
        ) : (
          <span className="font-medium">{t("clients.detail.subscriptionIndividual")}</span>
        )}
      </div>
      <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={onChange}>
        {t("clients.issue.change")}
      </Button>
    </div>
  );
}
