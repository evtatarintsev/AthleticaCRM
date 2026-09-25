import { useForm } from "@tanstack/react-form";
import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { BranchDetailResponse, BranchId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { AuthLayout } from "@/ui/AuthLayout";
import type { AuthApi } from "./authApi";
import {
  submitBranch,
  submitCredentials,
  type Credentials,
  type LoginFailure,
  type LoginOutcome,
} from "./loginFlow";

/** Значения формы входа. */
export interface LoginFormValues {
  readonly username: string;
  readonly password: string;
}

/** Схема формы входа с сообщениями на языке интерфейса [t]; логин обрезается по краям. */
function loginSchema(t: I18n["t"]) {
  return z.object({
    username: z.string().trim().min(1, t("error.required")),
    password: z.string().min(1, t("error.required")),
  });
}

/** Шаг экрана входа: ввод учётных данных или выбор филиала. */
type LoginStep =
  | { readonly kind: "credentials"; readonly failure: LoginFailure | null }
  | {
      readonly kind: "branch";
      readonly credentials: Credentials;
      readonly branches: readonly BranchDetailResponse[];
      readonly loading: boolean;
      readonly failure: LoginFailure | null;
    };

/**
 * Экран входа. [api] — клиент авторизации, [onAuthenticated] — переход после успешного входа.
 * Поля размечены `autocomplete`, чтобы работали автозаполнение и менеджеры паролей.
 */
export function LoginPage({ api, onAuthenticated }: { api: AuthApi; onAuthenticated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<LoginStep>({ kind: "credentials", failure: null });
  const schema = useMemo(() => loginSchema(t), [t]);

  const apply = (outcome: LoginOutcome, credentials: Credentials) => {
    switch (outcome.kind) {
      case "authenticated":
        onAuthenticated();
        return;
      case "chooseBranch":
        setStep({
          kind: "branch",
          credentials,
          branches: outcome.branches,
          loading: false,
          failure: null,
        });
        return;
      case "failed":
        setStep((s) => ({ ...s, loading: false, failure: outcome.failure }));
        return;
    }
  };

  const defaultValues: LoginFormValues = { username: "", password: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setStep({ kind: "credentials", failure: null });
      apply(await submitCredentials(api, parsed.data), parsed.data);
    },
  });

  if (step.kind === "branch") {
    const onBranch = async (branchId: BranchId) => {
      setStep({ ...step, loading: true, failure: null });
      apply(await submitBranch(api, step.credentials, branchId), step.credentials);
    };
    return (
      <AuthLayout title={t("auth.branchTitle")} subtitle={t("auth.branchSubtitle")}>
        <div className="space-y-3">
          {step.failure !== null && <FormAlert message={failureMessage(t, step.failure)} />}
          <ul className="space-y-2">
            {step.branches.map((branch) => (
              <li key={branch.id}>
                <Button
                  type="button"
                  variant="outline"
                  size="lg"
                  disabled={step.loading}
                  onClick={() => {
                    void onBranch(branch.id);
                  }}
                  className="w-full justify-start"
                >
                  {branch.name}
                </Button>
              </li>
            ))}
          </ul>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setStep({ kind: "credentials", failure: null });
            }}
            className="w-full"
          >
            {t("action.back")}
          </Button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={t("auth.loginTitle")}
      subtitle={t("auth.loginSubtitle")}
      footer={
        <>
          {t("auth.noAccount")}{" "}
          <Link to="/sign-up" className="font-medium text-primary hover:underline">
            {t("auth.actionRegister")}
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
        {step.failure !== null && <FormAlert message={failureMessage(t, step.failure)} />}
        <form.Field name="username">
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
        <form.Field name="password">
          {(field) => (
            <PasswordField
              field={field}
              label={t("auth.password")}
              autoComplete="current-password"
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
              {t("auth.actionLogin")}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </AuthLayout>
  );
}

/** Текст причины неудачного входа [failure] на языке интерфейса [t]. */
function failureMessage(t: I18n["t"], failure: LoginFailure): string {
  switch (failure.kind) {
    case "invalidCredentials":
      return t("error.invalidCredentials");
    case "noBranches":
      return t("error.noBranches");
    case "unavailable":
      return t("error.serviceUnavailable");
    case "server":
      return failure.message;
  }
}
