import { useForm } from "@tanstack/react-form";
import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiError } from "@/api/client";
import { CurrencySchema, type Currency } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField, SelectField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { CURRENCIES } from "@/lib/currency";
import { AuthLayout } from "@/ui/AuthLayout";
import type { AuthApi } from "./authApi";
import { availableTimezones, browserTimezone } from "./timezones";

/** Значения формы регистрации. */
export interface SignUpFormValues {
  readonly companyName: string;
  readonly userName: string;
  readonly login: string;
  readonly password: string;
  readonly timezone: string;
  readonly currency: Currency;
}

/** Схема формы регистрации с сообщениями на языке интерфейса [t]; текст обрезается по краям. */
function signUpSchema(t: I18n["t"]) {
  const required = z.string().trim().min(1, t("error.required"));
  return z.object({
    companyName: required,
    userName: required,
    login: z
      .string()
      .trim()
      .pipe(z.email(t("error.invalidEmail"))),
    password: z.string().min(1, t("error.required")),
    timezone: required,
    currency: CurrencySchema,
  });
}

/**
 * Экран регистрации организации. [api] — клиент авторизации,
 * [onAuthenticated] — переход после успешной регистрации (сервер сразу выдаёт сессию).
 */
export function SignUpPage({
  api,
  onAuthenticated,
}: {
  api: AuthApi;
  onAuthenticated: () => void;
}) {
  const { t } = useI18n();
  const schema = useMemo(() => signUpSchema(t), [t]);
  const defaultTimezone = useMemo(() => browserTimezone(), []);
  const timezones = useMemo(
    () => availableTimezones(defaultTimezone).map((zone) => ({ value: zone, label: zone })),
    [defaultTimezone],
  );
  const [failure, setFailure] = useState<ApiError | null>(null);

  const defaultValues: SignUpFormValues = {
    companyName: "",
    userName: "",
    login: "",
    password: "",
    timezone: defaultTimezone,
    currency: "RUB",
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.signUp(parsed.data);
      if (result.ok) {
        onAuthenticated();
      } else {
        setFailure(result.error);
      }
    },
  });

  return (
    <AuthLayout
      title={t("auth.registerTitle")}
      subtitle={t("auth.registerSubtitle")}
      footer={
        <>
          {t("auth.haveAccount")}{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            {t("auth.actionLogin")}
          </Link>
        </>
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
        {failure !== null && <FormAlert message={signUpErrorMessage(t, failure)} />}
        <form.Field name="companyName">
          {(field) => (
            <TextField
              field={field}
              label={t("auth.orgName")}
              autoComplete="organization"
              required
              autoFocus
            />
          )}
        </form.Field>
        <form.Field name="userName">
          {(field) => (
            <TextField field={field} label={t("auth.yourName")} autoComplete="name" required />
          )}
        </form.Field>
        <form.Field name="login">
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
            />
          )}
        </form.Field>
        <form.Field name="password">
          {(field) => (
            <PasswordField
              field={field}
              label={t("auth.password")}
              autoComplete="new-password"
              required
            />
          )}
        </form.Field>
        <form.Field name="timezone">
          {(field) => <SelectField field={field} label={t("auth.timezone")} options={timezones} />}
        </form.Field>
        <form.Field name="currency">
          {(field) => (
            <SelectField
              field={field}
              label={t("auth.currency")}
              hint={t("auth.currencyHint")}
              options={CURRENCIES.map((c) => ({ value: c.code, label: `${c.code} (${c.symbol})` }))}
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
              {t("auth.actionRegister")}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </AuthLayout>
  );
}

/** Текст ошибки регистрации [error]: сообщение сервера, если оно есть. */
function signUpErrorMessage(t: I18n["t"], error: ApiError): string {
  switch (error.kind) {
    case "business":
      return error.message === "" ? t("error.registrationFailed") : error.message;
    case "unauthenticated":
      return t("error.registrationFailed");
    case "unavailable":
    case "contract":
      return t("error.serviceUnavailable");
  }
}
