import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ChannelConfig, ChannelIntegrationSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, SelectField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";

/** Провайдер канала — тип поля `type` в `ChannelConfig`. */
export type ChannelProvider = ChannelConfig["type"];

/** Провайдеры в порядке показа в выпадающем списке. */
const PROVIDERS: readonly ChannelProvider[] = ["in_app", "smsc_sms", "telegram_bot", "twilio_sms"];

/** Настройки канала из формы: имя, включённость и конфигурация провайдера. */
export interface ChannelTerms {
  readonly name: string;
  readonly enabled: boolean;
  readonly config: ChannelConfig;
}

/** Значения формы: провайдер-специфичные поля есть у всех провайдеров сразу, лишние не отправляются. */
interface ChannelFormValues {
  readonly name: string;
  readonly enabled: boolean;
  readonly provider: ChannelProvider;
  readonly login: string;
  readonly password: string;
  readonly botToken: string;
  readonly accountSid: string;
  readonly authToken: string;
  readonly from: string;
}

/** Конфигурация провайдера [provider] со значениями пустой строки. */
function defaultConfig(provider: ChannelProvider): ChannelConfig {
  switch (provider) {
    case "in_app":
      return { type: "in_app" };
    case "smsc_sms":
      return { type: "smsc_sms", login: "", password: "" };
    case "telegram_bot":
      return { type: "telegram_bot", botToken: "" };
    case "twilio_sms":
      return { type: "twilio_sms", accountSid: "", authToken: "", from: "" };
  }
}

/** Значения формы из существующей конфигурации [config]. */
function valuesFromConfig(
  config: ChannelConfig,
): Pick<
  ChannelFormValues,
  "provider" | "login" | "password" | "botToken" | "accountSid" | "authToken" | "from"
> {
  const blank = { login: "", password: "", botToken: "", accountSid: "", authToken: "", from: "" };
  switch (config.type) {
    case "in_app":
      return { ...blank, provider: "in_app" };
    case "smsc_sms":
      return { ...blank, provider: "smsc_sms", login: config.login, password: config.password };
    case "telegram_bot":
      return { ...blank, provider: "telegram_bot", botToken: config.botToken };
    case "twilio_sms":
      return {
        ...blank,
        provider: "twilio_sms",
        accountSid: config.accountSid,
        authToken: config.authToken,
        from: config.from,
      };
  }
}

/** Конфигурация из значений формы [values] для провайдера `values.provider`. */
function configFromValues(values: ChannelFormValues): ChannelConfig {
  switch (values.provider) {
    case "in_app":
      return { type: "in_app" };
    case "smsc_sms":
      return { type: "smsc_sms", login: values.login.trim(), password: values.password };
    case "telegram_bot":
      return { type: "telegram_bot", botToken: values.botToken.trim() };
    case "twilio_sms":
      return {
        type: "twilio_sms",
        accountSid: values.accountSid.trim(),
        authToken: values.authToken.trim(),
        from: values.from.trim(),
      };
  }
}

/** Схема формы канала с сообщениями на языке [t]: провайдер-специфичные поля обязательны. */
function channelSchema(t: I18n["t"]) {
  return z
    .object({
      name: z.string().trim().min(1, t("error.required")),
      enabled: z.boolean(),
      provider: z.enum(PROVIDERS),
      login: z.string(),
      password: z.string(),
      botToken: z.string(),
      accountSid: z.string(),
      authToken: z.string(),
      from: z.string(),
    })
    .superRefine((value, ctx) => {
      type CredentialField =
        "login" | "password" | "botToken" | "accountSid" | "authToken" | "from";
      const required: Readonly<Record<ChannelProvider, readonly CredentialField[]>> = {
        in_app: [],
        smsc_sms: ["login", "password"],
        telegram_bot: ["botToken"],
        twilio_sms: ["accountSid", "authToken", "from"],
      };
      required[value.provider].forEach((field) => {
        if (value[field].trim() === "") {
          ctx.addIssue({ code: "custom", path: [field], message: t("error.required") });
        }
      });
    });
}

/**
 * Диалог создания или изменения канала связи (паритет с `ChannelsScreen`/`ChannelEditor`
 * KMP-клиента): провайдер выбирается только при создании, поля учётных данных зависят от
 * провайдера, включённость переключается независимо.
 */
export function ChannelDialog({
  title,
  initial,
  onSave,
  onClose,
}: {
  readonly title: string;
  readonly initial: ChannelIntegrationSchema | null;
  readonly onSave: (terms: ChannelTerms) => Promise<string | null>;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const schema = useMemo(() => channelSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const defaultValues: ChannelFormValues = {
    name: initial?.name ?? "",
    enabled: initial?.enabled ?? true,
    ...valuesFromConfig(initial?.config ?? defaultConfig("in_app")),
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      setFailure(null);
      const error = await onSave({
        name: value.name.trim(),
        enabled: value.enabled,
        config: configFromValues(value),
      });
      if (error === null) {
        onClose();
      } else {
        setFailure(error);
      }
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
          <DialogTitle>{title}</DialogTitle>
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
          <form.Field name="name">
            {(field) => (
              <TextField
                field={field}
                label={t("channels.field.name")}
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <form.Field name="provider">
            {(field) => (
              <SelectField
                field={field}
                label={t("channels.field.provider")}
                options={PROVIDERS.map((provider) => ({
                  value: provider,
                  label: t(`channels.provider.${provider}`),
                }))}
              />
            )}
          </form.Field>
          <form.Subscribe selector={(state) => state.values.provider}>
            {(provider) => (
              <>
                {provider === "smsc_sms" && (
                  <>
                    <form.Field name="login">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("channels.field.login")}
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                    <form.Field name="password">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("channels.field.password")}
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                  </>
                )}
                {provider === "telegram_bot" && (
                  <form.Field name="botToken">
                    {(field) => (
                      <TextField
                        field={field}
                        label={t("channels.field.botToken")}
                        autoComplete="off"
                        required
                      />
                    )}
                  </form.Field>
                )}
                {provider === "twilio_sms" && (
                  <>
                    <form.Field name="accountSid">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("channels.field.accountSid")}
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                    <form.Field name="authToken">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("channels.field.authToken")}
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                    <form.Field name="from">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("channels.field.from")}
                          autoComplete="off"
                          required
                        />
                      )}
                    </form.Field>
                  </>
                )}
              </>
            )}
          </form.Subscribe>
          <form.Field name="enabled">
            {(field) => <CheckboxField field={field} label={t("channels.field.enabled")} />}
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
