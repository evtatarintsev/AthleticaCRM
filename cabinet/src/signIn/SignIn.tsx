import { useEffect, useState, type SyntheticEvent } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import { errorText } from "../api/errorText";
import { useI18n } from "../i18n/context";

/** Шаг входа: номер, код или принятие условий перед повтором входа. */
type Step =
  | { readonly kind: "phone" }
  | {
      readonly kind: "code";
      readonly phone: string;
      readonly termsVersion: string;
      readonly retryAfterSeconds: number;
      readonly issue: number;
    }
  | {
      readonly kind: "terms";
      readonly phone: string;
      readonly code: string;
      readonly termsVersion: string;
    };

/**
 * Вход в кабинет по номеру и одноразовому коду через [api]. Условия показываются, только если
 * сервер ответил `CLIENT_APP_TERMS_REQUIRED`: тогда вход повторяется с принятой версией.
 * [onSignedIn] — сеанс открыт.
 */
export function SignIn({ api, onSignedIn }: { api: CabinetApi; onSignedIn: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<Step>({ kind: "phone" });
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** Запрашивает код на номер [target]; [viaSms] — по SMS вместо Telegram. */
  async function requestCode(target: string, viaSms: boolean) {
    setBusy(true);
    setError(null);
    const result = await api.call("client-app/auth/request-code", { phone: target, viaSms });
    setBusy(false);
    if (!result.ok) {
      setError(errorText(result.error, t));
      return;
    }
    setCode("");
    setStep({
      kind: "code",
      phone: target,
      termsVersion: result.value.termsVersion,
      retryAfterSeconds: result.value.retryAfterSeconds,
      issue: step.kind === "code" ? step.issue + 1 : 0,
    });
  }

  /** Входит номером [target] и кодом [value]; [acceptedTermsVersion] — принятая версия условий. */
  async function login(
    target: string,
    value: string,
    termsVersion: string,
    acceptedTermsVersion: string | null,
  ) {
    setBusy(true);
    setError(null);
    const result = await api.call("client-app/auth/login", {
      phone: target,
      code: value,
      acceptedTermsVersion,
    });
    setBusy(false);
    if (result.ok) {
      onSignedIn();
      return;
    }
    if (result.error.kind === "business" && result.error.code === "CLIENT_APP_TERMS_REQUIRED") {
      setStep({ kind: "terms", phone: target, code: value, termsVersion });
      return;
    }
    setError(errorText(result.error, t));
  }

  /** Обработчик формы: действие [action] без перезагрузки страницы. */
  function submit(action: () => Promise<void>) {
    return (event: SyntheticEvent) => {
      event.preventDefault();
      void action();
    };
  }

  switch (step.kind) {
    case "phone":
      return (
        <form className="card" onSubmit={submit(() => requestCode(phone, false))}>
          <h1>{t("signIn.phoneTitle")}</h1>
          <p className="muted">{t("signIn.phoneHint")}</p>
          <label>
            {t("signIn.phoneLabel")}
            <input
              type="tel"
              autoComplete="tel"
              required
              value={phone}
              onChange={(event) => {
                setPhone(event.target.value);
              }}
            />
          </label>
          {error !== null && <p role="alert">{error}</p>}
          <button type="submit" disabled={busy}>
            {t("signIn.getCode")}
          </button>
        </form>
      );
    case "code":
      return (
        <form
          className="card"
          onSubmit={submit(() => login(step.phone, code, step.termsVersion, null))}
        >
          <h1>{t("signIn.codeTitle")}</h1>
          <p className="muted">{t("signIn.codeSent", { phone: step.phone })}</p>
          <label>
            {t("signIn.codeLabel")}
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              required
              value={code}
              onChange={(event) => {
                setCode(event.target.value);
              }}
            />
          </label>
          {error !== null && <p role="alert">{error}</p>}
          <button type="submit" disabled={busy}>
            {t("signIn.login")}
          </button>
          <Resend
            key={step.issue}
            retryAfterSeconds={step.retryAfterSeconds}
            busy={busy}
            onResend={(viaSms) => void requestCode(step.phone, viaSms)}
          />
          <button
            type="button"
            className="link"
            onClick={() => {
              setError(null);
              setStep({ kind: "phone" });
            }}
          >
            {t("signIn.changePhone")}
          </button>
        </form>
      );
    case "terms":
      return (
        <form
          className="card"
          onSubmit={submit(() =>
            login(step.phone, step.code, step.termsVersion, step.termsVersion),
          )}
        >
          <h1>{t("signIn.termsTitle")}</h1>
          <p>{t("signIn.termsText")}</p>
          {error !== null && <p role="alert">{error}</p>}
          <button type="submit" disabled={busy}>
            {t("signIn.termsAccept")}
          </button>
        </form>
      );
  }
}

/**
 * Повторная отправка кода: [retryAfterSeconds] секунд обратного отсчёта с момента показа, затем
 * «Отправить ещё раз» и «Отправить по SMS». [onResend] получает признак SMS. Новый код — новый
 * экземпляр компонента (`key`), поэтому отсчёт начинается заново.
 */
function Resend({
  retryAfterSeconds,
  busy,
  onResend,
}: {
  retryAfterSeconds: number;
  busy: boolean;
  onResend: (viaSms: boolean) => void;
}) {
  const { t } = useI18n();
  const [retryAt] = useState(() => Date.now() + retryAfterSeconds * 1000);
  const [now, setNow] = useState(() => Date.now());
  const left = Math.max(0, Math.ceil((retryAt - now) / 1000));

  useEffect(() => {
    if (left === 0) {
      return;
    }
    const timer = window.setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => {
      window.clearInterval(timer);
    };
  }, [left]);

  if (left > 0) {
    const time = `${String(Math.floor(left / 60))}:${String(left % 60).padStart(2, "0")}`;
    return <p className="muted">{t("signIn.retryIn", { time })}</p>;
  }
  return (
    <div className="row">
      <button
        type="button"
        className="secondary"
        disabled={busy}
        onClick={() => {
          onResend(false);
        }}
      >
        {t("signIn.resend")}
      </button>
      <button
        type="button"
        className="secondary"
        disabled={busy}
        onClick={() => {
          onResend(true);
        }}
      >
        {t("signIn.viaSms")}
      </button>
    </div>
  );
}
