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
  type ActivationMode,
  type BranchId,
  type ClientId,
  type CloseMode,
  type Currency,
  type DurationUnit,
  type GroupId,
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
  readonly price: string;
  readonly activationMode: ActivationMode;
  /** Дата активации для режима «с даты». */
  readonly activateOn: string;
  /** Необязательная крайняя дата для режима «при первом посещении». */
  readonly activationDeadline: string;
  readonly closeMode: CloseMode;
  /** Группы, в которых действует абонемент. */
  readonly groupIds: readonly GroupId[];
}

/** Группа, которую можно выбрать для абонемента. */
interface GroupOption {
  readonly id: GroupId;
  readonly name: string;
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
      price: z.string().refine((v) => parseMoney(v, currency) !== null, t("error.invalidAmount")),
      activationMode: z.enum(["IMMEDIATE", "ON_DATE", "ON_FIRST_VISIT"]),
      activateOn: z.string(),
      activationDeadline: z.string(),
      closeMode: z.enum(["MANUAL", "ON_LAST_SESSION", "ON_EXPIRY"]),
      groupIds: z.custom<readonly GroupId[]>((value) => Array.isArray(value)),
    })
    .refine((v) => v.unlimited || positiveInt(v.sessions) !== null, {
      path: ["sessions"],
      message: t("error.positiveInteger"),
    })
    .refine((v) => v.groupIds.length > 0, {
      path: ["groupIds"],
      message: t("clients.issue.groupsRequired"),
    })
    .refine((v) => v.activationMode !== "ON_DATE" || v.activateOn !== "", {
      path: ["activateOn"],
      message: t("error.required"),
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

/**
 * Значения формы по шаблону [template]: активация «сразу», закрытие «вручную»,
 * отмечены группы [groupIds]; дата для режима «с даты» по умолчанию — сегодня.
 */
function valuesFrom(template: Template, groupIds: readonly GroupId[]): IssueSubscriptionFormValues {
  const modes = {
    activationMode: "IMMEDIATE",
    activateOn: todayLocalDate(),
    activationDeadline: "",
    closeMode: "MANUAL",
    groupIds,
  } as const;
  switch (template.kind) {
    case "tariff":
      return {
        unlimited: template.tariff.sessions === null,
        sessions: template.tariff.sessions?.toString() ?? "",
        durationValue: template.tariff.durationValue.toString(),
        durationUnit: template.tariff.durationUnit,
        price: moneyEditText(template.tariff.price),
        ...modes,
      };
    case "individual":
      return {
        unlimited: false,
        sessions: "",
        durationValue: "1",
        durationUnit: "MONTHS",
        price: "",
        ...modes,
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
  const client = useQuery(apiQuery(api, branchId, "clients/detail", { id: clientId }));
  const groups = useQuery(apiQuery(api, branchId, "groups/list", {}));
  if (settings.isError || client.isError || groups.isError) {
    return (
      <EditSheetBody>
        <FormAlert message={t("directory.loadError")} />
      </EditSheetBody>
    );
  }
  if (settings.data === undefined || client.data === undefined || groups.data === undefined) {
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
      groupOptions={groupOptions(client.data.groups, groups.data.groups)}
      clientGroupIds={client.data.groups.map((group) => group.id)}
    />
  );
}

/** Группы для выбора: группы клиента [clientGroups] и группы филиала [branchGroups] без повторов. */
function groupOptions(
  clientGroups: readonly GroupOption[],
  branchGroups: readonly GroupOption[],
): readonly GroupOption[] {
  const seen = new Set<string>();
  return [...clientGroups, ...branchGroups]
    .filter((group) => {
      if (seen.has(group.id)) {
        return false;
      }
      seen.add(group.id);
      return true;
    })
    .map(({ id, name }) => ({ id, name }));
}

/**
 * Шаги выдачи в валюте [currency]; группы — из [groupOptions], по умолчанию отмечены
 * группы клиента [clientGroupIds]. Форма условий живёт на обоих шагах: повторный выбор того же
 * шаблона сохраняет введённые значения, выбор другого заполняет поля его условиями заново.
 */
function IssueSubscriptionSteps({
  api,
  branchId,
  clientId,
  currency,
  groupOptions: options,
  clientGroupIds,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly currency: Currency;
  readonly groupOptions: readonly GroupOption[];
  readonly clientGroupIds: readonly GroupId[];
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close, reportGuard } = useEditSheet();
  const tariffsQuery = apiQuery(api, branchId, "tariffs/list", {});
  const tariffs = useQuery(tariffsQuery);
  const schema = useMemo(() => issueSubscriptionSchema(t, currency), [t, currency]);
  const [step, setStep] = useState<"pick" | "terms">("pick");
  const [chosen, setChosen] = useState<Template | null>(null);
  const [defaults, setDefaults] = useState(() =>
    valuesFrom({ kind: "individual" }, clientGroupIds),
  );
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
        groupIds: value.groupIds,
        durationValue,
        durationUnit: value.durationUnit,
        price,
        activationMode: value.activationMode,
        activateOn:
          value.activationMode === "ON_DATE" ? LocalDateSchema.parse(value.activateOn) : null,
        activationDeadline:
          value.activationMode === "ON_FIRST_VISIT" && value.activationDeadline !== ""
            ? LocalDateSchema.parse(value.activationDeadline)
            : null,
        closeMode:
          value.unlimited && value.closeMode === "ON_LAST_SESSION" ? "MANUAL" : value.closeMode,
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
      const values = valuesFrom(template, clientGroupIds);
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
          selector={(state) => ({
            dirty: state.isDirty,
            submitting: state.isSubmitting,
            noGroups: state.values.groupIds.length === 0,
          })}
        >
          {({ dirty: formDirty, submitting, noGroups }) => (
            <EditSheetForm
              dirty={formDirty}
              submitting={submitting}
              submitDisabled={noGroups}
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
              <form.Field name="groupIds">
                {(field) => (
                  <GroupsField
                    options={options}
                    value={field.state.value}
                    onChange={(next) => {
                      field.handleChange(next);
                    }}
                  />
                )}
              </form.Field>
              <form.Field
                name="unlimited"
                listeners={{
                  onChange: ({ value: unlimited }) => {
                    if (unlimited && form.getFieldValue("closeMode") === "ON_LAST_SESSION") {
                      form.setFieldValue("closeMode", "MANUAL");
                    }
                  },
                }}
              >
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
              <form.Field name="activationMode">
                {(field) => (
                  <RadioGroupField
                    field={field}
                    label={t("clients.issue.activationMode")}
                    options={[
                      { value: "IMMEDIATE", label: t("membership.activation.IMMEDIATE") },
                      { value: "ON_DATE", label: t("membership.activation.ON_DATE") },
                      { value: "ON_FIRST_VISIT", label: t("membership.activation.ON_FIRST_VISIT") },
                    ]}
                  />
                )}
              </form.Field>
              <form.Subscribe selector={(state) => state.values.activationMode}>
                {(activationMode) => {
                  switch (activationMode) {
                    case "IMMEDIATE":
                      return null;
                    case "ON_DATE":
                      return (
                        <form.Field name="activateOn">
                          {(field) => (
                            <TextField
                              field={field}
                              type="date"
                              label={t("clients.issue.activateOn")}
                              autoComplete="off"
                              required
                            />
                          )}
                        </form.Field>
                      );
                    case "ON_FIRST_VISIT":
                      return (
                        <form.Field name="activationDeadline">
                          {(field) => (
                            <TextField
                              field={field}
                              type="date"
                              label={t("clients.issue.activationDeadline")}
                              hint={t("clients.issue.activationDeadlineHint")}
                              autoComplete="off"
                            />
                          )}
                        </form.Field>
                      );
                  }
                }}
              </form.Subscribe>
              <form.Subscribe selector={(state) => state.values.unlimited}>
                {(unlimited) => (
                  <form.Field name="closeMode">
                    {(field) => (
                      <RadioGroupField
                        field={field}
                        label={t("clients.issue.closeMode")}
                        options={[
                          { value: "MANUAL", label: t("membership.close.MANUAL") },
                          {
                            value: "ON_LAST_SESSION",
                            label: t("membership.close.ON_LAST_SESSION"),
                            disabled: unlimited,
                          },
                          { value: "ON_EXPIRY", label: t("membership.close.ON_EXPIRY") },
                        ]}
                      />
                    )}
                  </form.Field>
                )}
              </form.Subscribe>
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

/**
 * Выбор групп абонемента из [options] с отмеченными [value]; изменения — через [onChange].
 * Без отмеченных групп показывает, что нужна хотя бы одна.
 */
function GroupsField({
  options,
  value,
  onChange,
}: {
  readonly options: readonly GroupOption[];
  readonly value: readonly GroupId[];
  readonly onChange: (next: readonly GroupId[]) => void;
}) {
  const { t } = useI18n();
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-sm font-medium">{t("clients.issue.groups")}</legend>
      {options.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("clients.issue.noGroups")}</p>
      )}
      <div className="space-y-1">
        {options.map((group) => (
          <label key={group.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={value.includes(group.id)}
              onChange={(event) => {
                onChange(
                  event.target.checked
                    ? [...value, group.id]
                    : value.filter((id) => id !== group.id),
                );
              }}
              className="size-4 accent-primary"
            />
            {group.name}
          </label>
        ))}
      </div>
      {value.length === 0 && (
        <p role="status" className="text-xs text-destructive">
          {t("clients.issue.groupsRequired")}
        </p>
      )}
    </fieldset>
  );
}
