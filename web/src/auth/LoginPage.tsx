import { useState, type SubmitEvent } from "react";
import { Link } from "react-router";
import { authApi, type AuthApi } from "../api/client";
import type { BranchDetailResponse } from "../api/schemas";
import { t } from "../i18n";
import { formText } from "../lib/forms";
import { AuthLayout } from "../ui/AuthLayout";
import { ErrorAlert, PrimaryButton } from "../ui/controls";
import { PasswordField, TextField } from "../ui/fields";
import { submitBranch, submitCredentials, type Credentials, type LoginOutcome } from "./loginFlow";
import { redirectToApp } from "./session";

/** Состояние экрана входа: ввод учётных данных или выбор филиала. */
type LoginState =
  | { step: "credentials"; loading: boolean; error: string | null }
  | {
      step: "branch";
      credentials: Credentials;
      branches: BranchDetailResponse[];
      loading: boolean;
      error: string | null;
    };

/**
 * Экран входа. [api] — клиент авторизации, [onAuthenticated] — переход после успешного входа.
 * Поля неуправляемые и размечены `autocomplete`, чтобы работало автозаполнение браузера.
 */
export function LoginPage({
  api = authApi,
  onAuthenticated = redirectToApp,
}: {
  api?: AuthApi;
  onAuthenticated?: () => void;
}) {
  const [state, setState] = useState<LoginState>({
    step: "credentials",
    loading: false,
    error: null,
  });

  const apply = (outcome: LoginOutcome, credentials: Credentials) => {
    switch (outcome.kind) {
      case "authenticated":
        onAuthenticated();
        return;
      case "chooseBranch":
        setState({
          step: "branch",
          credentials,
          branches: outcome.branches,
          loading: false,
          error: null,
        });
        return;
      case "error":
        setState((s) => ({ ...s, loading: false, error: outcome.message }));
        return;
    }
  };

  const onCredentials = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const credentials = {
      username: formText(form, "username").trim(),
      password: formText(form, "password"),
    };
    setState({ step: "credentials", loading: true, error: null });
    apply(await submitCredentials(api, credentials), credentials);
  };

  if (state.step === "branch") {
    const onBranch = async (branchId: string) => {
      setState({ ...state, loading: true, error: null });
      apply(await submitBranch(api, state.credentials, branchId), state.credentials);
    };
    return (
      <AuthLayout title={t.branchTitle} subtitle={t.branchSubtitle}>
        <div className="space-y-3">
          {state.error !== null && <ErrorAlert message={state.error} />}
          <ul className="space-y-2">
            {state.branches.map((branch) => (
              <li key={branch.id}>
                <button
                  type="button"
                  disabled={state.loading}
                  onClick={() => {
                    void onBranch(branch.id);
                  }}
                  className="w-full rounded-lg border border-slate-200 px-4 py-3 text-left font-medium transition hover:border-brand-500 hover:bg-brand-50 disabled:opacity-60 dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  {branch.name}
                </button>
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => {
              setState({ step: "credentials", loading: false, error: null });
            }}
            className="w-full py-2 text-sm text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"
          >
            {t.actionBack}
          </button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t.loginTitle}
      subtitle={t.loginSubtitle}
      footer={
        <>
          {t.noAccount}{" "}
          <Link to="/sign-up" className="font-medium text-brand-600 hover:underline">
            {t.actionRegister}
          </Link>
        </>
      }
    >
      <form
        method="post"
        onSubmit={(event) => {
          void onCredentials(event);
        }}
        className="space-y-4"
      >
        {state.error !== null && <ErrorAlert message={state.error} />}
        <TextField
          label={t.email}
          name="username"
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          autoFocus
        />
        <PasswordField
          label={t.password}
          name="password"
          autoComplete="current-password"
          required
        />
        <PrimaryButton type="submit" loading={state.loading}>
          {t.actionLogin}
        </PrimaryButton>
      </form>
    </AuthLayout>
  );
}
