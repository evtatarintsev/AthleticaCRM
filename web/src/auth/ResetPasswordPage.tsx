import { useForm } from "@tanstack/react-form";
import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { AuthLayout } from "@/ui/AuthLayout";

/** Значения формы нового пароля. */
interface ResetPasswordFormValues {
  readonly newPassword: string;
  readonly confirmPassword: string;
}

/** Схема формы нового пароля с сообщениями на языке [t]: пароль задан и повторён. */
function resetPasswordSchema(t: I18n["t"]) {
  return z
    .object({
      newPassword: z.string().min(1, t("error.required")),
      confirmPassword: z.string().min(1, t("error.required")),
    })
    .refine((value) => value.newPassword === value.confirmPassword, {
      path: ["confirmPassword"],
      message: t("error.passwordsDontMatch"),
    });
}

/**
 * Экран нового пароля по ссылке из письма с токеном [token]. Открытие страницы ничего не меняет:
 * пароль сбрасывается только отправкой формы, после чего пользователь входит заново.
 */
export function ResetPasswordPage({ api, token }: { api: ApiClient; token: string }) {
  const { t } = useI18n();
  const schema = useMemo(() => resetPasswordSchema(t), [t]);
  const [done, setDone] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: ResetPasswordFormValues = { newPassword: "", confirmPassword: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("auth/reset-password", {
        token,
        newPassword: parsed.data.newPassword,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      setDone(true);
    },
  });

  if (done) {
    return (
      <AuthLayout title={t("passwordReset.title")} subtitle={t("passwordReset.subtitle")}>
        <div className="space-y-4">
          <p role="status">{t("passwordReset.done")}</p>
          <Button asChild size="lg" className="w-full">
            <Link to="/login">{t("auth.actionLogin")}</Link>
          </Button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t("passwordReset.title")}
      subtitle={t("passwordReset.subtitle")}
      footer={
        <Link to="/forgot-password" className="font-medium text-primary hover:underline">
          {t("passwordReset.requestNew")}
        </Link>
      }
    >
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
        <form.Field name="newPassword">
          {(field) => (
            <PasswordField
              field={field}
              label={t("password.new")}
              autoComplete="new-password"
              required
              autoFocus
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
            <Button
              type="submit"
              size="lg"
              disabled={submitting}
              aria-busy={submitting}
              className="w-full"
            >
              {t("passwordReset.submit")}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </AuthLayout>
  );
}
