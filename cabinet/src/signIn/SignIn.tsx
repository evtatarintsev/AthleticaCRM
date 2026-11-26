import { useEffect, useState, type SyntheticEvent } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import { errorText } from "../api/errorText";
import { useI18n } from "../i18n/context";

/** Шаг входа: номер, код или принятие условий перед повтором входа. */
type Step =
  | { readonly kind: "phone" }
  | {
      readonly kind: "code" | "terms";
      readonly phone: string;
      readonly termsVersion: string;
      readonly retryAt: number;
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
      retryAt: Date.now() + result.value.retryAfterSeconds * 1000,
    });
  }

  /** Входит номером и кодом формы; [acceptedTermsVersion] — принятая версия условий. */
  async function login(acceptedTermsVersion: string | null) {
    if (step.kind === "phone") {
      return;
    }
    setBusy(true);
    setError(null);
    const result = await api.call("client-app/auth/login", {
      phone: step.phone,
      code,
      acceptedTermsVersion,
    });
    setBusy(false);
    if (result.ok) {
      onSignedIn();
      return;
    }
    if (result.error.kind === "business" && result.error.code === "CLIENT_APP_TERMS_REQUIRED") {
      setStep({ ...step, kind: "terms" });
      return;
    }
    if (step.kind === "terms" && result.error.kind === "business") {
      setStep({ ...step, kind: "code" });
    }
    setError(errorText(result.error, t));
  }

  /** Обработчик формы: действие [action] без перезагрузки страницы. */
  function submit(event: SyntheticEvent, action: () => Promise<void>) {
    event.preventDefault();
    void action();
  }

  switch (step.kind) {
    case "phone":
      return (
        <form
          className="card"
          onSubmit={(event) => {
            submit(event, () => requestCode(phone, false));
          }}
        >
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
          onSubmit={(event) => {
            submit(event, () => login(null));
          }}
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
            retryAt={step.retryAt}
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
          onSubmit={(event) => {
            submit(event, () => login(step.termsVersion));
          }}
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
 * Повторная отправка кода: отсчёт до [retryAt], затем «Отправить ещё раз» и «Отправить по SMS».
 * [onResend] получает признак SMS. Переход к условиям и обратно не сбрасывает отсчёт.
 */
function Resend({
  retryAt,
  busy,
  onResend,
}: {
  retryAt: number;
  busy: boolean;
  onResend: (viaSms: boolean) => void;
}) {
  const { t } = useI18n();
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
