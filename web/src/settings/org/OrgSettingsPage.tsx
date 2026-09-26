import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { SelectField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";

/** Часовые пояса, доступные в браузере, отсортированные по названию. */
const TIMEZONES: readonly string[] = [...Intl.supportedValuesOf("timeZone")].sort();

const orgSettingsSchema = (requiredMessage: string) =>
  z.object({
    name: z.string().trim().min(1, requiredMessage),
    timezone: z.string(),
  });

/**
 * Основные настройки организации (паритет с `OrgBasicSettingsScreen` KMP-клиента):
 * название и часовой пояс редактируются, валюта только показывается — задаётся при
 * регистрации и после недоступна для изменения.
 */
export function OrgSettingsPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "org/settings");
  const settings = useQuery(query);
  const [saved, setSaved] = useState(false);

  return (
    <section className="max-w-md">
      <PageHeader title={t("orgSettings.title")} />
      {settings.isError && <FormAlert message={t("orgSettings.loadError")} />}
      {settings.isPending && (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}
      {settings.data !== undefined && (
        <OrgSettingsForm
          name={settings.data.name}
          timezone={settings.data.timezone}
          currency={settings.data.currency}
          saved={saved}
          onSave={async (values) => {
            setSaved(false);
            const result = await api.call("org/settings/update", values);
            if (!result.ok) {
              return apiErrorMessage(t, result.error);
            }
            queryClient.setQueryData(query.queryKey, result.value);
            setSaved(true);
            return null;
          }}
        />
      )}
    </section>
  );
}

/** Форма основных настроек: название и часовой пояс. */
function OrgSettingsForm({
  name,
  timezone,
  currency,
  saved,
  onSave,
}: {
  readonly name: string;
  readonly timezone: string;
  readonly currency: string;
  readonly saved: boolean;
  readonly onSave: (values: { name: string; timezone: string }) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const [failure, setFailure] = useState<string | null>(null);
  const schema = orgSettingsSchema(t("error.required"));
  const form = useForm({
    defaultValues: { name, timezone },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      setFailure(null);
      const error = await onSave({ name: value.name.trim(), timezone: value.timezone });
      if (error !== null) {
        setFailure(error);
      }
    },
  });

  return (
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
      {saved && <p className="text-sm text-primary">{t("orgSettings.saved")}</p>}
      <form.Field name="name">
        {(field) => (
          <TextField field={field} label={t("orgSettings.name")} autoComplete="off" required />
        )}
      </form.Field>
      <form.Field name="timezone">
        {(field) => (
          <SelectField
            field={field}
            label={t("orgSettings.timezone")}
            options={TIMEZONES.map((zone) => ({ value: zone, label: zone }))}
          />
        )}
      </form.Field>
      <div className="space-y-1.5">
        <Label htmlFor="org-currency">{t("orgSettings.currency")}</Label>
        <Input id="org-currency" value={currency} disabled readOnly />
        <p className="text-xs text-muted-foreground">{t("orgSettings.currencyHint")}</p>
      </div>
      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(submitting) => (
          <Button type="submit" disabled={submitting} aria-busy={submitting}>
            {t("action.save")}
          </Button>
        )}
      </form.Subscribe>
    </form>
  );
}
