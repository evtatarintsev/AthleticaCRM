import { useCallback } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import type {
  ClientAppMembershipSchema,
  ClientAppMembershipsResponse,
  ClientAppProfileSchema,
} from "../api/generated/contracts";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { useLoaded } from "../ui/useLoaded";

/** Абонемент действует или ждёт активации — показывается среди текущих, а не в истории. */
function isCurrent(membership: ClientAppMembershipSchema) {
  return membership.state === "ACTIVE" || membership.state === "INACTIVE";
}

/**
 * Формат дня без времени на языке [locale]: дата приходит без часового пояса, поэтому
 * форматируется в UTC — иначе западнее UTC она сдвинулась бы на день.
 */
function dayFormat(locale: string) {
  return new Intl.DateTimeFormat(locale, { day: "numeric", month: "long", timeZone: "UTC" });
}

/** Остаток, срок, «заканчивается» и заморозка абонемента [membership] — строки под названием. */
function Details({ membership }: { membership: ClientAppMembershipSchema }) {
  const { t, locale } = useI18n();
  const days = dayFormat(locale);
  const day = (date: string) => days.format(new Date(date));
  const sessions =
    membership.sessionsTotal === null || membership.sessionsRemaining === null
      ? t("memberships.unlimited")
      : t("memberships.left", {
          left: membership.sessionsRemaining,
          total: membership.sessionsTotal,
        });
  return (
    <>
      <p>
        {sessions}
        {membership.state === "ACTIVE" &&
          membership.endDate !== null &&
          `, ${t("memberships.until", { date: day(membership.endDate) })}`}
      </p>
      {membership.endingSoon.length > 0 && <p className="ending">{t("memberships.endingSoon")}</p>}
      {membership.freeze !== null && (
        <p className="muted">
          {t("memberships.frozen", {
            from: day(membership.freeze.startDate),
            to: day(membership.freeze.endDate),
          })}
        </p>
      )}
      {membership.state === "INACTIVE" && (
        <p className="muted">
          {membership.activateOn === null
            ? t("memberships.startsOnVisit")
            : t("memberships.startsOn", { date: day(membership.activateOn) })}
        </p>
      )}
      {membership.state === "CLOSED" && membership.closedOn !== null && (
        <p className="muted">{t("memberships.closedOn", { date: day(membership.closedOn) })}</p>
      )}
      {membership.state === "REVOKED" && <p className="muted">{t("memberships.revoked")}</p>}
    </>
  );
}

/**
 * Блок текущего абонемента профиля [profile] на главной, загруженного через [api]: активный, иначе
 * неактивированный; без них блок скрыт. [onOpen] — открыть все абонементы с уже загруженным ответом.
 */
export function MembershipBlock({
  api,
  profile,
  onOpen,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onOpen: (memberships: ClientAppMembershipsResponse) => void;
}) {
  const { t } = useI18n();
  const { orgId, clientId } = profile;
  const { state, reload } = useLoaded<ClientAppMembershipsResponse>(
    useCallback(
      () => api.call("client-app/memberships", { orgId, clientId }),
      [api, orgId, clientId],
    ),
  );
  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      break;
  }
  const all = state.value.memberships;
  const current =
    all.find((it) => it.state === "ACTIVE") ?? all.find((it) => it.state === "INACTIVE");
  if (current === undefined) {
    return null;
  }
  return (
    <section className="card">
      <h2>{t("memberships.current")}</h2>
      <strong>{current.name}</strong>
      <Details membership={current} />
      <button
        type="button"
        className="link"
        onClick={() => {
          onOpen(state.value);
        }}
      >
        {t("memberships.all")}
      </button>
    </section>
  );
}

/** Абонемент [membership] с раскрываемой историей списаний. */
function Membership({ membership }: { membership: ClientAppMembershipSchema }) {
  const { t, locale } = useI18n();
  const days = dayFormat(locale);
  return (
    <li className="membership">
      <strong>{membership.name}</strong>
      <Details membership={membership} />
      <details>
        <summary>{t("memberships.debits", { count: membership.debits.length })}</summary>
        {membership.debits.length === 0 ? (
          <p className="muted">{t("memberships.noDebits")}</p>
        ) : (
          <ul className="sessions">
            {membership.debits.map((it) => (
              <li key={it.sessionId} className="session">
                <span>{days.format(new Date(it.sessionDate))}</span>
                {it.groupName !== null && <span className="muted">{it.groupName}</span>}
              </li>
            ))}
          </ul>
        )}
      </details>
    </li>
  );
}

/** Экран абонементов [memberships]: текущие, затем история; у каждого — списания. [onBack] — назад. */
export function Memberships({
  memberships,
  onBack,
}: {
  memberships: ClientAppMembershipsResponse;
  onBack: () => void;
}) {
  const { t } = useI18n();
  const current = memberships.memberships.filter(isCurrent);
  const history = memberships.memberships.filter((it) => !isCurrent(it));
  return (
    <section className="card">
      <h1>{t("memberships.title")}</h1>
      {current.length === 0 ? (
        <p className="muted">{t("memberships.none")}</p>
      ) : (
        <ul className="memberships">
          {current.map((it) => (
            <Membership key={it.id} membership={it} />
          ))}
        </ul>
      )}
      {history.length > 0 && (
        <>
          <h2>{t("memberships.history")}</h2>
          <ul className="memberships">
            {history.map((it) => (
              <Membership key={it.id} membership={it} />
            ))}
          </ul>
        </>
      )}
      <button type="button" className="link" onClick={onBack}>
        {t("memberships.back")}
      </button>
    </section>
  );
}
