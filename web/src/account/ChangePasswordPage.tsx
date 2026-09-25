import { useForm } from "@tanstack/react-form";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { sessionQuery } from "@/query/queries";
import { PageHeader } from "@/ui/PageHeader";

/** Значения формы смены пароля. */
interface ChangePasswordFormValues {
  readonly oldPassword: string;
  readonly newPassword: string;
  readonly confirmPassword: string;
}

/** Схема формы смены пароля с сообщениями на языке [t]: поля заполнены, новый пароль повторён. */
function changePasswordSchema(t: I18n["t"]) {
  return z
    .object({
      oldPassword: z.string().min(1, t("error.required")),
      newPassword: z.string().min(1, t("error.required")),
      confirmPassword: z.string().min(1, t("error.required")),
    })
    .refine((value) => value.newPassword === value.confirmPassword, {
      path: ["confirmPassword"],
      message: t("error.passwordsDontMatch"),
    });
}

/**
 * Смена пароля текущего пользователя. Поля размечены `current-password` / `new-password`,
 * а скрытое поле логина подсказывает менеджеру паролей, для какой учётной записи сохранить пароль.
 */
export function ChangePasswordPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const schema = useMemo(() => changePasswordSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: ChangePasswordFormValues = {
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value, formApi }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("auth/me/change-password", {
        oldPassword: parsed.data.oldPassword,
        newPassword: parsed.data.newPassword,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      formApi.reset();
      toast.success(t("password.changed"));
    },
  });

  return (
    <section className="max-w-md">
      <PageHeader title={t("password.title")} />
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
        <input
          type="text"
          name="username"
          autoComplete="username"
          value={me.username}
          readOnly
          hidden
        />
        <form.Field name="oldPassword">
          {(field) => (
            <PasswordField
              field={field}
              label={t("password.current")}
              autoComplete="current-password"
              required
            />
          )}
        </form.Field>
        <form.Field name="newPassword">
          {(field) => (
            <PasswordField
              field={field}
              label={t("password.new")}
              autoComplete="new-password"
              required
            />
          )}
        </form.Field>
        <form.Field name="confirmPassword">
          {(field) => (
            <PasswordField
              field={field}
              label={t("password.confirm")}
              autoComplete="new-password"
              required
            />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {t("action.save")}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </section>
  );
}
