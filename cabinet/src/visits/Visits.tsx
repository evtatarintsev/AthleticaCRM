import { useCallback, useState } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import {
  LocalDateSchema,
  type ClientAppProfileSchema,
  type ClientAppVisitsResponse,
  type LocalDate,
} from "../api/generated/contracts";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { useLoaded } from "../ui/useLoaded";

/** Первое число месяца, сдвинутого от [date] на [months]; считается в UTC, чтобы пояс браузера не сдвигал день. */
function shiftMonth(date: LocalDate, months: number): LocalDate {
  const shifted = new Date(`${date.slice(0, 7)}-01T00:00:00Z`);
  shifted.setUTCMonth(shifted.getUTCMonth() + months);
  return LocalDateSchema.parse(shifted.toISOString().slice(0, 10));
}

/**
 * Блок посещений текущего месяца профиля [profile] на главной, загруженных через [api]: число посещений
 * и ссылка на историю. [onOpen] — открыть историю посещений.
 */
export function VisitsBlock({
  api,
  profile,
  onOpen,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onOpen: () => void;
}) {
  const { t } = useI18n();
  const { orgId, clientId } = profile;
  const { state, reload } = useLoaded<ClientAppVisitsResponse>(
    useCallback(() => api.call("client-app/visits", { orgId, clientId }), [api, orgId, clientId]),
  );
  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      return (
        <section className="card">
          <h2>{t("visits.month")}</h2>
          <p>
            <strong>{t("visits.count", { count: state.value.presentCount })}</strong>
          </p>
          <button type="button" className="link" onClick={onOpen}>
            {t("visits.open")}
          </button>
        </section>
      );
  }
}

/**
 * История посещений профиля [profile] по месяцам через [api]: проведённые занятия с отметкой
 * «был» / «не был». [onBack] — назад на главную.
 */
export function Visits({
  api,
  profile,
  onBack,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onBack: () => void;
}) {
  const { t, locale } = useI18n();
  const { orgId, clientId } = profile;
  const [monthOf, setMonthOf] = useState<LocalDate | null>(null);
  const { state, reload } = useLoaded<ClientAppVisitsResponse>(
    useCallback(
      () => api.call("client-app/visits", { orgId, clientId, monthOf }),
      [api, orgId, clientId, monthOf],
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
  const month = state.value;
  const title = new Intl.DateTimeFormat(locale, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
  const day = new Intl.DateTimeFormat(locale, {
    weekday: "short",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  });
  return (
    <section className="card">
      <h1>{t("visits.title")}</h1>
      <div className="week">
        <button
          type="button"
          className="secondary"
          aria-label={t("visits.previous")}
          onClick={() => {
            setMonthOf((it) => shiftMonth(it ?? month.monthStart, -1));
          }}
        >
          ←
        </button>
        <span>{title.format(new Date(month.monthStart))}</span>
        <button
          type="button"
          className="secondary"
          aria-label={t("visits.following")}
          onClick={() => {
            setMonthOf((it) => shiftMonth(it ?? month.monthStart, 1));
          }}
        >
          →
        </button>
      </div>
      {month.visits.length === 0 ? (
        <p className="muted">{t("visits.empty")}</p>
      ) : (
        <>
          <p>{t("visits.count", { count: month.presentCount })}</p>
          <ul className="sessions">
            {month.visits.map((it) => (
              <li key={it.sessionId} className="session">
                <div>
                  <strong>
                    {day.format(new Date(it.date))}, {it.startTime.slice(0, 5)}
                  </strong>
                  {it.groupName !== null && <span className="muted">{it.groupName}</span>}
                </div>
                {it.presence === "PRESENT" ? (
                  <span>{t("visits.present")}</span>
                ) : (
                  <span className="ending">{t("visits.absent")}</span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      <button type="button" className="link" onClick={onBack}>
        {t("visits.back")}
      </button>
    </section>
  );
}
