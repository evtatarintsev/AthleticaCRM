import { useForm } from "@tanstack/react-form";
import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { AuthLayout } from "@/ui/AuthLayout";

/** Значения формы запроса сброса пароля. */
interface ForgotPasswordFormValues {
  readonly email: string;
}

/** Схема формы запроса сброса с сообщениями на языке [t]; email обрезается по краям. */
function forgotPasswordSchema(t: I18n["t"]) {
  return z.object({ email: z.string().trim().min(1, t("error.required")) });
}

/**
 * Экран запроса письма со ссылкой сброса пароля. После отправки показывает одно и то же
 * сообщение для любого адреса — сервер не раскрывает, зарегистрирован ли он.
 */
export function ForgotPasswordPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const schema = useMemo(() => forgotPasswordSchema(t), [t]);
  const [sent, setSent] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: ForgotPasswordFormValues = { email: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("auth/forgot-password", { email: parsed.data.email });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      setSent(true);
    },
  });

  return (
    <AuthLayout
      title={t("passwordReset.requestTitle")}
      subtitle={t("passwordReset.requestSubtitle")}
      footer={
        <Link to="/login" className="font-medium text-primary hover:underline">
          {t("passwordReset.backToLogin")}
        </Link>
      }
    >
      {sent ? (
        <p role="status">{t("passwordReset.sent")}</p>
      ) : (
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
          <form.Field name="email">
            {(field) => (
              <TextField
                field={field}
                label={t("auth.email")}
                type="email"
                inputMode="email"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                required
                autoFocus
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
                {t("passwordReset.send")}
              </Button>
            )}
          </form.Subscribe>
        </form>
      )}
    </AuthLayout>
  );
}
