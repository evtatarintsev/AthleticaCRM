import { useCallback, useState } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import { errorText } from "../api/errorText";
import type {
  ClientAppHomeResponse,
  ClientAppInvitationsResponse,
  ClientAppProfileSchema,
  ClientAppProfilesResponse,
} from "../api/generated/contracts";
import { Account } from "../account/Account";
import { Balance, BalanceBlock } from "../balance/Balance";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { useLoaded } from "../ui/useLoaded";
import { selectedProfile, storedProfile, storeProfile, type ProfileKey } from "./selectedProfile";

/** Экран вошедшего человека: главная выбранного профиля, его баланс, переключатель или приглашения. */
type View = "home" | "balance" | "switcher" | "invitations";

/**
 * Кабинет вошедшего человека через [api]: профили и экран «Аккаунт» с сеансами, выходом и удалением
 * аккаунта. [onSignedOut] — сеанс этого браузера завершён, нужно показать вход.
 */
export function Cabinet({ api, onSignedOut }: { api: CabinetApi; onSignedOut: () => void }) {
  const { t } = useI18n();
  const [account, setAccount] = useState(false);
  if (account) {
    return (
      <Account
        api={api}
        onBack={() => {
          setAccount(false);
        }}
        onSignedOut={onSignedOut}
      />
    );
  }
  return (
    <>
      <Profiles api={api} />
      <button
        type="button"
        className="link"
        onClick={() => {
          setAccount(true);
        }}
      >
        {t("account.open")}
      </button>
    </>
  );
}

/**
 * Профили аккаунта через [api]: главная выбранного профиля, переключатель профилей и
 * неподключённые приглашения. Выбор профиля запоминается на устройстве.
 */
function Profiles({ api }: { api: CabinetApi }) {
  const { t } = useI18n();
  const [view, setView] = useState<View>("home");
  const [selection, setSelection] = useState<ProfileKey | null>(storedProfile);
  const { state, reload } = useLoaded(useCallback(() => api.call("client-app/profiles"), [api]));

  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      break;
  }
  const profiles: ClientAppProfilesResponse = state.value;
  const profile = selectedProfile(profiles.profiles, selection);
  const showInvitations =
    view === "invitations" || (profile === null && profiles.newInvitations > 0);

  if (showInvitations) {
    return (
      <Invitations
        api={api}
        onBack={
          profile === null
            ? null
            : () => {
                setView("home");
              }
        }
        onChanged={() => {
          setView("home");
          reload();
        }}
      />
    );
  }
  if (profile === null) {
    return <p className="card">{t("profiles.empty")}</p>;
  }
  if (view === "balance") {
    return (
      <Balance
        api={api}
        profile={profile}
        onBack={() => {
          setView("home");
        }}
      />
    );
  }
  if (view === "switcher") {
    return (
      <Switcher
        profiles={profiles}
        current={profile}
        onSelect={(next) => {
          storeProfile(next);
          setSelection(next);
          setView("home");
        }}
        onInvitations={() => {
          setView("invitations");
        }}
      />
    );
  }
  return (
    <>
      <header className="profile">
        <div>
          <strong>{profile.clientName}</strong>
          <span className="muted">{profile.orgName}</span>
        </div>
        <button
          type="button"
          className="secondary"
          onClick={() => {
            setView("switcher");
          }}
        >
          {t("profiles.switch")}
          {profiles.newInvitations > 0 && <span className="badge">{profiles.newInvitations}</span>}
        </button>
      </header>
      <Home
        key={`${profile.orgId}/${profile.clientId}`}
        api={api}
        profile={profile}
        onBalance={() => {
          setView("balance");
        }}
      />
    </>
  );
}

/** Главная профиля [profile]: приветствие по имени клиента и баланс; [onBalance] — открыть баланс. */
function Home({
  api,
  profile,
  onBalance,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onBalance: () => void;
}) {
  const { t } = useI18n();
  const { orgId, clientId } = profile;
  const { state, reload } = useLoaded<ClientAppHomeResponse>(
    useCallback(() => api.call("client-app/home", { orgId, clientId }), [api, orgId, clientId]),
  );
  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      return (
        <>
          <section className="card">
            <h1>{t("home.greeting")}</h1>
            <p>{t("home.cabinetOf", { name: state.value.clientName })}</p>
          </section>
          <BalanceBlock api={api} profile={profile} onOpen={onBalance} />
        </>
      );
  }
}

/**
 * Переключатель: подключённые профили [profiles] по организациям, текущий [current] отмечен,
 * и пункт «Новые приглашения» со счётчиком, если они есть.
 */
function Switcher({
  profiles,
  current,
  onSelect,
  onInvitations,
}: {
  profiles: ClientAppProfilesResponse;
  current: ClientAppProfileSchema;
  onSelect: (profile: ClientAppProfileSchema) => void;
  onInvitations: () => void;
}) {
  const { t } = useI18n();
  const orgs = [...new Set(profiles.profiles.map((it) => it.orgId))];
  return (
    <section className="card">
      <h1>{t("profiles.title")}</h1>
      {orgs.map((orgId) => {
        const inOrg = profiles.profiles.filter((it) => it.orgId === orgId);
        return (
          <div key={orgId} className="group">
            <h2>{inOrg[0]?.orgName}</h2>
            {inOrg.map((it) => (
              <button
                key={it.clientId}
                type="button"
                className="option"
                aria-current={it.orgId === current.orgId && it.clientId === current.clientId}
                onClick={() => {
                  onSelect(it);
                }}
              >
                {it.clientName}
              </button>
            ))}
          </div>
        );
      })}
      {profiles.newInvitations > 0 && (
        <button type="button" className="option" onClick={onInvitations}>
          {t("profiles.newInvitations")}
          <span className="badge">{profiles.newInvitations}</span>
        </button>
      )}
      <p className="muted">{t("profiles.isolation")}</p>
    </section>
  );
}

/**
 * Неподключённые приглашения с «Подключить» и «Это не я»; без данных клиентов.
 * [onChanged] — человек ответил на приглашение; [onBack] — вернуться, если есть куда.
 */
function Invitations({
  api,
  onBack,
  onChanged,
}: {
  api: CabinetApi;
  onBack: (() => void) | null;
  onChanged: () => void;
}) {
  const { t, locale } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { state, reload } = useLoaded<ClientAppInvitationsResponse>(
    useCallback(() => api.call("client-app/invitations"), [api]),
  );
  const dates = new Intl.DateTimeFormat(locale, { day: "numeric", month: "long" });

  /** Отвечает на приглашение действием [path]. */
  async function answer(
    path: "client-app/connect" | "client-app/not-me",
    orgId: ClientAppProfileSchema["orgId"],
  ) {
    setBusy(true);
    setError(null);
    const result = await api.call(path, { orgId });
    setBusy(false);
    if (result.ok) {
      onChanged();
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
      return (
        <section className="card">
          <h1>{t("invitations.title")}</h1>
          <p className="muted">{t("invitations.hint")}</p>
          {state.value.invitations.map((it) => (
            <article key={it.orgId} className="invitation">
              <h2>{it.orgName}</h2>
              <p className="muted">
                {t("invitations.invitedAt", { date: dates.format(new Date(it.invitedAt)) })}
                {" · "}
                {t("invitations.clients", { count: it.clientCount })}
              </p>
              <div className="row">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void answer("client-app/connect", it.orgId)}
                >
                  {t("invitations.connect")}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => void answer("client-app/not-me", it.orgId)}
                >
                  {t("invitations.notMe")}
                </button>
              </div>
            </article>
          ))}
          {error !== null && <p role="alert">{error}</p>}
          {onBack !== null && (
            <button type="button" className="link" onClick={onBack}>
              {t("invitations.back")}
            </button>
          )}
        </section>
      );
  }
}
