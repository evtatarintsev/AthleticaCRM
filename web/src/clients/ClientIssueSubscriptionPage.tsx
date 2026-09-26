import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  MembershipIdSchema,
  type ClientId,
  type Currency,
  type DurationUnit,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { moneyEditText, parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { todayLocalDate } from "./clientsQueries";

/** Значение «тариф» в поле выбора: id тарифа или индивидуальный абонемент. */
const INDIVIDUAL = "individual";

interface IssueSubscriptionFormValues {
  readonly tariff: string;
  readonly unlimited: boolean;
  readonly sessions: string;
  readonly durationValue: string;
  readonly durationUnit: DurationUnit;
  readonly startDate: string;
  readonly price: string;
}

const MAX_INT32 = 2_147_483_647;

function positiveInt(text: string): number | null {
  const value = /^\d+$/.test(text.trim()) ? Number(text.trim()) : Number.NaN;
  return Number.isInteger(value) && value > 0 && value <= MAX_INT32 ? value : null;
}

function issueSubscriptionSchema(t: I18n["t"], currency: Currency) {
  return z
    .object({
      tariff: z.string(),
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

/**
 * Выдача абонемента клиенту: выбор тарифа (или индивидуальные параметры), количество
 * занятий, срок действия, дата начала и стоимость. Оплата на сервер не передаётся —
 * это отдельная задача (см. `IssueSubscriptionViewModel` в KMP-клиенте).
 */
export function ClientIssueSubscriptionPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const session = useSession(api);
  const branchId = session.currentBranch.id;
  const currency = session.orgInfo.balance?.currency ?? "RUB";
  const tariffsQuery = useQuery(apiQuery(api, branchId, "tariffs/list", {}));
  const tariffs = (tariffsQuery.data?.tariffs ?? []).filter((tariff) => !tariff.archived);

  const schema = useMemo(() => issueSubscriptionSchema(t, currency), [t, currency]);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: IssueSubscriptionFormValues = {
    tariff: INDIVIDUAL,
    unlimited: false,
    sessions: "",
    durationValue: "1",
    durationUnit: "MONTHS",
    startDate: todayLocalDate(),
    price: "",
  };

  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const durationValue = positiveInt(value.durationValue);
      const price = parseMoney(value.price, currency);
      const sessions = value.unlimited ? null : positiveInt(value.sessions);
      if (durationValue === null || price === null || (!value.unlimited && sessions === null)) {
        return;
      }
      const selectedTariff = tariffs.find((tariff) => tariff.id === value.tariff);
      setFailure(null);
      const result = await api.call("memberships/issue", {
        id: MembershipIdSchema.parse(uuidv7()),
        clientId,
        tariffPlanId: selectedTariff?.id ?? null,
        name: selectedTariff?.name ?? t("clients.detail.subscriptionIndividual"),
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
      await queryClient.invalidateQueries({
        queryKey: ["api", branchId, "memberships/list", { clientId }],
      });
      await navigate({ to: "/clients/$clientId", params: { clientId } });
    },
  });

  return (
    <section className="max-w-xl space-y-4">
      <PageHeader title={t("clients.detail.issueSubscription")} />
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
        <form.Field name="tariff">
          {(field) => (
            <div className="space-y-1.5">
              <Label htmlFor={field.name}>{t("clients.detail.subscriptionTariff")}</Label>
              <NativeSelect
                id={field.name}
                name={field.name}
                value={field.state.value}
                onChange={(event) => {
                  const tariffId = event.target.value;
                  field.handleChange(tariffId);
                  const tariff = tariffs.find((candidate) => candidate.id === tariffId);
                  if (tariff === undefined) {
                    return;
                  }
                  form.setFieldValue("unlimited", tariff.sessions === null);
                  form.setFieldValue("sessions", tariff.sessions?.toString() ?? "");
                  form.setFieldValue("durationValue", tariff.durationValue.toString());
                  form.setFieldValue("durationUnit", tariff.durationUnit);
                  form.setFieldValue("price", moneyEditText(tariff.price));
                }}
              >
                <option value={INDIVIDUAL}>{t("clients.detail.subscriptionIndividual")}</option>
                {tariffs.map((tariff) => (
                  <option key={tariff.id} value={tariff.id}>
                    {tariff.name}
                  </option>
                ))}
              </NativeSelect>
            </div>
          )}
        </form.Field>
        <form.Field name="unlimited">
          {(field) => (
            <CheckboxField field={field} label={t("clients.detail.subscriptionUnlimitedLabel")} />
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
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {t("clients.detail.issueSubscription")}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </section>
  );
}
