import { useForm } from "@tanstack/react-form";
import { useMemo, useState, type ReactNode } from "react";
import { z } from "zod";
import type {
  ChannelConfigChange,
  ChannelIntegrationId,
  ChannelSettingsSchemaSmscSms,
  ChannelSettingsSchemaTelegramBot,
  ChannelSettingsSchemaTwilioSms,
  NewChannelConfig,
} from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Сохранение канала из формы: создание с полными настройками или изменение с частичными. */
export type ChannelSave =
  | { readonly kind: "create"; readonly name: string; readonly config: NewChannelConfig }
  | {
      readonly kind: "update";
      readonly id: ChannelIntegrationId;
      readonly name: string;
      readonly config: ChannelConfigChange;
    };

/**
 * Обработка сохранения [ChannelSave]: `null` — успех, панель закрывается; строка — текст
 * ошибки, форма остаётся открытой.
 */
export type ChannelSaveHandler = (save: ChannelSave) => Promise<string | null>;

/** Сохранённый канал провайдера с настройками [S] — исходные данные формы изменения. */
export interface SavedChannel<S> {
  readonly id: ChannelIntegrationId;
  readonly name: string;
  readonly settings: S;
}

/** Свойства формы провайдера: [initial] `null` — создание нового канала. */
interface ProviderFormProps<S> {
  readonly initial: SavedChannel<S> | null;
  readonly onSave: ChannelSaveHandler;
  /** Шапка над полями: провайдер и кнопка «Назад»; получает признак несохранённых изменений. */
  readonly header: (dirty: boolean) => ReactNode;
}

/** Новый секрет из поля [value]; пустое поле — `null`, сохранённое значение не меняется. */
function changedSecret(value: string): string | null {
  return value.trim() === "" ? null : value;
}

/** Схема поля секрета на языке [t]: обязателен только при создании ([creating]). */
function secretSchema(t: I18n["t"], creating: boolean) {
  return creating ? z.string().trim().min(1, t("error.required")) : z.string();
}

/** Схема обязательного текстового поля на языке [t]. */
function requiredSchema(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}

/**
 * Общая рамка формы провайдера: шапка, ошибка сохранения, кнопки панели. После успешного
 * сохранения закрывает панель.
 */
function ProviderFormFrame({
  header,
  dirty,
  submitting,
  failure,
  onSubmit,
  children,
}: {
  readonly header: ReactNode;
  readonly dirty: boolean;
  readonly submitting: boolean;
  readonly failure: string | null;
  readonly onSubmit: () => void;
  readonly children: ReactNode;
}) {
  return (
    <EditSheetForm dirty={dirty} submitting={submitting} onSubmit={onSubmit}>
      {header}
      {failure !== null && <FormAlert message={failure} />}
      {children}
    </EditSheetForm>
  );
}

/**
 * Отправка формы: сохраняет через [onSave], при успехе закрывает панель, при ошибке
 * возвращает её текст для показа в форме.
 */
function useProviderSubmit(onSave: ChannelSaveHandler) {
  const { close } = useEditSheet();
  const [failure, setFailure] = useState<string | null>(null);
  const submit = async (save: ChannelSave): Promise<void> => {
    setFailure(null);
    const error = await onSave(save);
    if (error === null) {
      close();
    } else {
      setFailure(error);
    }
  };
  return { failure, submit };
}

/** Форма канала SMSC: логин и пароль; при изменении пустой пароль оставляет сохранённый. */
export function SmscChannelForm({
  initial,
  onSave,
  header,
}: ProviderFormProps<ChannelSettingsSchemaSmscSms>) {
  const { t } = useI18n();
  const creating = initial === null;
  const { failure, submit } = useProviderSubmit(onSave);
  const schema = useMemo(
    () =>
      z.object({
        name: requiredSchema(t),
        login: requiredSchema(t),
        password: secretSchema(t, creating),
      }),
    [t, creating],
  );
  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      login: initial?.settings.login ?? "",
      password: "",
    },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const name = value.name.trim();
      const login = value.login.trim();
      await submit(
        creating
          ? { kind: "create", name, config: { type: "smsc_sms", login, password: value.password } }
          : {
              kind: "update",
              id: initial.id,
              name,
              config: { type: "smsc_sms", login, password: changedSecret(value.password) },
            },
      );
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <ProviderFormFrame
          header={header(dirty)}
          dirty={dirty}
          submitting={submitting}
          failure={failure}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
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
              <PasswordField
                field={field}
                label={t("channels.field.password")}
                autoComplete="new-password"
                required={creating}
                {...(creating ? {} : { hint: t("channels.passwordKeepHint") })}
              />
            )}
          </form.Field>
        </ProviderFormFrame>
      )}
    </form.Subscribe>
  );
}

/** Форма Telegram-бота: токен; при изменении пустой токен оставляет сохранённый. */
export function TelegramChannelForm({
  initial,
  onSave,
  header,
}: ProviderFormProps<ChannelSettingsSchemaTelegramBot>) {
  const { t } = useI18n();
  const creating = initial === null;
  const { failure, submit } = useProviderSubmit(onSave);
  const schema = useMemo(
    () => z.object({ name: requiredSchema(t), botToken: secretSchema(t, creating) }),
    [t, creating],
  );
  const form = useForm({
    defaultValues: { name: initial?.name ?? "", botToken: "" },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const name = value.name.trim();
      await submit(
        creating
          ? {
              kind: "create",
              name,
              config: { type: "telegram_bot", botToken: value.botToken.trim() },
            }
          : {
              kind: "update",
              id: initial.id,
              name,
              config: { type: "telegram_bot", botToken: changedSecret(value.botToken.trim()) },
            },
      );
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <ProviderFormFrame
          header={header(dirty)}
          dirty={dirty}
          submitting={submitting}
          failure={failure}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
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
          <form.Field name="botToken">
            {(field) => (
              <PasswordField
                field={field}
                label={t("channels.field.botToken")}
                autoComplete="off"
                required={creating}
                {...(initial === null
                  ? {}
                  : {
                      placeholder: initial.settings.tokenMask,
                      hint: t("channels.secretKeepHint", { mask: initial.settings.tokenMask }),
                    })}
              />
            )}
          </form.Field>
        </ProviderFormFrame>
      )}
    </form.Subscribe>
  );
}

/** Форма канала Twilio: Account SID, auth token и номер; пустой auth token оставляет сохранённый. */
export function TwilioChannelForm({
  initial,
  onSave,
  header,
}: ProviderFormProps<ChannelSettingsSchemaTwilioSms>) {
  const { t } = useI18n();
  const creating = initial === null;
  const { failure, submit } = useProviderSubmit(onSave);
  const schema = useMemo(
    () =>
      z.object({
        name: requiredSchema(t),
        accountSid: requiredSchema(t),
        authToken: secretSchema(t, creating),
        from: requiredSchema(t),
      }),
    [t, creating],
  );
  const form = useForm({
    defaultValues: {
      name: initial?.name ?? "",
      accountSid: initial?.settings.accountSid ?? "",
      authToken: "",
      from: initial?.settings.from ?? "",
    },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const name = value.name.trim();
      const accountSid = value.accountSid.trim();
      const from = value.from.trim();
      await submit(
        creating
          ? {
              kind: "create",
              name,
              config: { type: "twilio_sms", accountSid, authToken: value.authToken.trim(), from },
            }
          : {
              kind: "update",
              id: initial.id,
              name,
              config: {
                type: "twilio_sms",
                accountSid,
                authToken: changedSecret(value.authToken.trim()),
                from,
              },
            },
      );
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <ProviderFormFrame
          header={header(dirty)}
          dirty={dirty}
          submitting={submitting}
          failure={failure}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
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
              <PasswordField
                field={field}
                label={t("channels.field.authToken")}
                autoComplete="off"
                required={creating}
                {...(initial === null
                  ? {}
                  : {
                      placeholder: initial.settings.authTokenMask,
                      hint: t("channels.secretKeepHint", { mask: initial.settings.authTokenMask }),
                    })}
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
        </ProviderFormFrame>
      )}
    </form.Subscribe>
  );
}
