import { useCallback, useState } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import type { ApiResult } from "../api/client";
import { errorText } from "../api/errorText";
import type { ClientAppSessionsResponse } from "../api/generated/contracts";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { useLoaded } from "../ui/useLoaded";

/**
 * Экран «Аккаунт»: сеансы аккаунта с завершением сеансов других устройств, выход из этого браузера
 * и удаление аккаунта с подтверждением. [onBack] — вернуться к профилям; [onSignedOut] — сеанс этого
 * браузера завершён (выход или удаление аккаунта).
 */
export function Account({
  api,
  onBack,
  onSignedOut,
}: {
  api: CabinetApi;
  onBack: () => void;
  onSignedOut: () => void;
}) {
  const { t, locale } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const { state, reload } = useLoaded<ClientAppSessionsResponse>(
    useCallback(() => api.call("client-app/sessions"), [api]),
  );
  const dates = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });

  /** Выполняет действие [action]; при успехе вызывает [onDone], иначе показывает ошибку. */
  async function run(action: () => Promise<ApiResult<undefined>>, onDone: () => void) {
    setBusy(true);
    setError(null);
    const result = await action();
    setBusy(false);
    if (result.ok) {
      onDone();
    } else {
      setError(errorText(result.error, t));
    }
  }

  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      break;
  }
  return (
    <section className="card">
      <h1>{t("account.title")}</h1>
      <h2>{t("account.sessions")}</h2>
      <ul className="sessions">
        {state.value.sessions.map((it) => (
          <li key={it.id} className="session">
            <div>
              <strong>{it.name ?? t("account.unknownDevice")}</strong>
              <span className="muted">
                {it.current
                  ? t("account.currentSession")
                  : t("account.lastSeen", { date: dates.format(new Date(it.lastSeenAt)) })}
              </span>
            </div>
            {!it.current && (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                aria-label={t("account.terminateNamed", {
                  name: it.name ?? t("account.unknownDevice"),
                })}
                onClick={() =>
                  void run(
                    () => api.call("client-app/sessions/terminate", { sessionId: it.id }),
                    reload,
                  )
                }
              >
                {t("account.terminate")}
              </button>
            )}
          </li>
        ))}
      </ul>
      <button
        type="button"
        className="secondary"
        disabled={busy}
        onClick={() => void run(() => api.call("client-app/auth/logout"), onSignedOut)}
      >
        {t("account.logout")}
      </button>
      {confirmDelete ? (
        <div className="danger" role="group" aria-label={t("account.delete")}>
          <p>{t("account.deleteWarning")}</p>
          <div className="row">
            <button
              type="button"
              className="destructive"
              disabled={busy}
              onClick={() => void run(() => api.call("client-app/account/delete"), onSignedOut)}
            >
              {t("account.deleteConfirm")}
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => {
                setConfirmDelete(false);
              }}
            >
              {t("account.deleteCancel")}
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          className="link"
          onClick={() => {
            setConfirmDelete(true);
          }}
        >
          {t("account.delete")}
        </button>
      )}
      {error !== null && <p role="alert">{error}</p>}
      <button type="button" className="link" onClick={onBack}>
        {t("account.back")}
      </button>
    </section>
  );
}
