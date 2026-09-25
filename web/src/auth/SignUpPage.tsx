import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router";
import { authApi, type ApiError, type AuthApi } from "../api/client";
import { CURRENCIES, type Currency } from "../api/schemas";
import { t } from "../i18n";
import { AuthLayout } from "../ui/AuthLayout";
import { ErrorAlert, PrimaryButton } from "../ui/controls";
import { PasswordField, SelectField, TextField } from "../ui/fields";
import { redirectToApp } from "./session";
import { availableTimezones, browserTimezone } from "./timezones";

/** Состояние отправки формы регистрации. */
type SubmitState = { loading: boolean; error: string | null };

/**
 * Экран регистрации организации. [api] — клиент авторизации,
 * [onAuthenticated] — переход после успешной регистрации (сервер сразу выдаёт сессию).
 */
export function SignUpPage({
  api = authApi,
  onAuthenticated = redirectToApp,
}: {
  api?: AuthApi;
  onAuthenticated?: () => void;
}) {
  const defaultTimezone = useMemo(() => browserTimezone(), []);
  const timezones = useMemo(
    () => availableTimezones(defaultTimezone).map((z) => ({ value: z, label: z })),
    [defaultTimezone],
  );
  const [state, setState] = useState<SubmitState>({ loading: false, error: null });

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const field = (name: string) => String(form.get(name) ?? "").trim();
    setState({ loading: true, error: null });
    const result = await api.signUp({
      companyName: field("organization"),
      userName: field("name"),
      login: field("username"),
      password: String(form.get("password") ?? ""),
      timezone: field("timezone"),
      currency: field("currency") as Currency,
    });
    if (result.ok) {
      onAuthenticated();
    } else {
      setState({ loading: false, error: signUpErrorMessage(result.error) });
    }
  };

  return (
    <AuthLayout
      title={t.registerTitle}
      subtitle={t.registerSubtitle}
      footer={
        <>
          {t.haveAccount}{" "}
          <Link to="/login" className="font-medium text-brand-600 hover:underline">
            {t.actionLogin}
          </Link>
        </>
      }
    >
      <form method="post" onSubmit={onSubmit} className="space-y-4">
        {state.error && <ErrorAlert message={state.error} />}
        <TextField
          label={t.orgName}
          name="organization"
          autoComplete="organization"
          required
          autoFocus
        />
        <TextField label={t.yourName} name="name" autoComplete="name" required />
        <TextField
          label={t.email}
          name="username"
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
        />
        <PasswordField label={t.password} name="password" autoComplete="new-password" required />
        <SelectField
          label={t.timezone}
          name="timezone"
          defaultValue={defaultTimezone}
          options={timezones}
        />
        <SelectField
          label={t.currency}
          name="currency"
          defaultValue="RUB"
          hint={t.currencyHint}
          options={CURRENCIES.map((c) => ({
            value: c.code,
            label: `${c.code} (${c.symbol})`,
          }))}
        />
        <PrimaryButton type="submit" loading={state.loading}>
          {t.actionRegister}
        </PrimaryButton>
      </form>
    </AuthLayout>
  );
}

/** Текст ошибки регистрации: сообщение сервера, если оно есть. */
function signUpErrorMessage(error: ApiError): string {
  switch (error.kind) {
    case "validation":
      return error.message || t.errorRegistrationFailed;
    case "unauthenticated":
      return t.errorRegistrationFailed;
    case "unavailable":
      return t.errorServiceUnavailable;
  }
}
